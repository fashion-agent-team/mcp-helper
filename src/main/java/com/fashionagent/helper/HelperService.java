package com.fashionagent.helper;

import com.fashionagent.helper.notify.AnswerNotifier;
import com.fashionagent.helper.photo.PhotoStore;
import com.fashionagent.helper.sms.SmsSender;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 연락처, 확인 요청 보내기, 답 받기, 만료. MCP 도구·REST·답장 페이지가 모두 이걸 부른다. */
@Service
public class HelperService {

    private static final Logger log = LoggerFactory.getLogger(HelperService.class);
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_ANSWER_LENGTH = 200;

    private final ContactRepository contacts;
    private final RequestRepository requests;
    private final PhotoStore photos;
    private final SmsSender sms;
    private final AnswerNotifier notifier;
    private final HelperProperties props;
    private final Clock clock;

    public HelperService(ContactRepository contacts, RequestRepository requests, PhotoStore photos, SmsSender sms,
            AnswerNotifier notifier, HelperProperties props, Clock clock) {
        this.contacts = contacts;
        this.requests = requests;
        this.photos = photos;
        this.sms = sms;
        this.notifier = notifier;
        this.props = props;
        this.clock = clock;
    }

    // ---------- 연락처 ----------

    public List<Contact.View> listContacts(UUID userId) {
        return contacts.findByUser(userId).stream().map(Contact::view).toList();
    }

    public Contact.View addContact(UUID userId, String name, String phone, String relation) {
        String n = trimToNull(name);
        if (n == null || n.length() > 50) {
            throw HelperException.badRequest("이름은 1~50자로 적어 주세요.");
        }
        String r = trimToNull(relation);
        if (r != null && r.length() > 30) {
            throw HelperException.badRequest("관계는 30자까지예요.");
        }
        String digits = Phones.normalize(phone);
        if (contacts.count(userId) >= props.maxContacts()) {
            throw HelperException.badRequest("가족·친구는 " + props.maxContacts() + "명까지 등록할 수 있어요.");
        }
        if (contacts.existsPhone(userId, digits)) {
            throw HelperException.badRequest("이미 등록한 번호예요.");
        }
        var contact = new Contact(Ids.contactId(), userId, n, digits, r);
        contacts.insert(contact);
        return contact.view();
    }

    public void deleteContact(UUID userId, String contactId) {
        if (contacts.find(userId, contactId).isEmpty()) {
            throw HelperException.notFound("없는 연락처예요.");
        }
        for (String key : requests.imageKeysOfContact(userId, contactId)) {
            deletePhotoQuietly(key);
        }
        contacts.delete(userId, contactId);
    }

    // ---------- 확인 요청 ----------

    public record Asked(String requestId, String expiresAt, String sentTo) {
    }

    public Asked ask(UUID userId, String contactId, String imageBase64, String question) {
        String q = trimToNull(question);
        if (q == null) {
            throw HelperException.badRequest("질문이 없어요.");
        }
        if (q.length() > props.maxQuestionLength()) {
            throw HelperException.badRequest("질문은 " + props.maxQuestionLength() + "자까지예요.");
        }
        byte[] jpeg = decodeJpeg(imageBase64);
        Contact contact = contacts.find(userId, contactId == null ? "" : contactId.trim())
                .orElseThrow(() -> HelperException.notFound("등록하지 않은 가족·친구예요."));
        Instant now = Instant.now(clock);
        if (requests.countSince(userId, now.minus(1, ChronoUnit.HOURS)) >= props.askLimitPerHour()) {
            throw new HelperException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "한 시간에 " + props.askLimitPerHour() + "번까지 물어볼 수 있어요.");
        }

        String id = Ids.requestId();
        String token = Ids.token();
        String imageKey = "requests/" + id + ".jpg";
        Instant expiresAt = now.plus(props.requestTtl()).truncatedTo(ChronoUnit.SECONDS);
        var request = new HelperRequest(id, userId, contact.id(), contact.name(), q, imageKey, token,
                HelperRequest.Status.pending, null, null, expiresAt, now);

        try {
            photos.put(imageKey, jpeg);
        } catch (RuntimeException e) {
            log.warn("사진 올리기 실패: request={} {}", id, e.toString());
            throw HelperException.upstreamFailed("사진을 올리지 못했어요.");
        }
        try {
            requests.insert(request);
        } catch (RuntimeException e) {
            deletePhotoQuietly(imageKey);
            throw e;
        }
        try {
            sms.send(contact.phone(), smsText(q, token));
        } catch (RuntimeException e) {
            // 보내지 못한 요청은 남기지 않는다(가족은 링크를 못 받았으니). 사진도 바로 지운다.
            log.warn("문자 보내기 실패: request={} {}", id, e.getMessage());
            requests.delete(id);
            deletePhotoQuietly(imageKey);
            throw HelperException.upstreamFailed("문자를 보내지 못했어요.");
        }
        return new Asked(id, seoul(expiresAt), contact.name());
    }

    String smsText(String question, String token) {
        return "[패션 에이전트] 옷 확인 부탁이 왔어요.\n\"" + question + "\"\n사진 보고 답해 주세요: " + link(token);
    }

    String link(String token) {
        return props.baseUrl() + "/h/" + token;
    }

    /** helper_answer 출력. pending이면 answer·answeredAt은 null. */
    public record Answer(String requestId, String status, String answer, String answeredBy, String answeredAt,
            String sentTo, String expiresAt) {
    }

    public Answer answerOf(UUID userId, String requestId) {
        HelperRequest r = requests.find(userId, requestId == null ? "" : requestId.trim())
                .orElseThrow(() -> HelperException.notFound("없는 요청이에요."));
        var status = r.statusAt(Instant.now(clock));
        boolean answered = status == HelperRequest.Status.answered;
        return new Answer(r.id(), status.name(),
                answered ? r.answer() : null,
                answered ? r.contactName() : null,
                answered ? seoul(r.answeredAt()) : null,
                r.contactName(), seoul(r.expiresAt()));
    }

    // ---------- 답장 페이지 (/h/<token>) ----------

    public Optional<HelperRequest> byToken(String token) {
        if (token == null || token.length() != 43) {
            return Optional.empty();
        }
        return requests.findByToken(token);
    }

    public Optional<byte[]> photoOf(String token) {
        Instant now = Instant.now(clock);
        return byToken(token)
                .filter(r -> r.statusAt(now) == HelperRequest.Status.pending && r.imageKey() != null)
                .flatMap(r -> photos.get(r.imageKey()));
    }

    /** 답 저장. 이미 답했거나 만료됐으면 false. 답을 받으면 사진은 바로 지운다. */
    public boolean submitAnswer(String token, String answer) {
        String a = trimToNull(answer);
        if (a == null) {
            throw HelperException.badRequest("답을 골라 주세요.");
        }
        if (a.length() > MAX_ANSWER_LENGTH) {
            a = a.substring(0, MAX_ANSWER_LENGTH);
        }
        Optional<HelperRequest> found = byToken(token);
        if (found.isEmpty() || !requests.answer(token, a, Instant.now(clock))) {
            return false;
        }
        HelperRequest r = found.get();
        if (r.imageKey() != null) {
            deletePhotoQuietly(r.imageKey());
            requests.clearImageKey(r.id());
        }
        try {
            notifier.answered(r);
        } catch (RuntimeException e) {
            log.warn("답 알림 실패: request={} {}", r.id(), e.toString());
        }
        return true;
    }

    // ---------- 만료 ----------

    /** 만료된 요청을 expired로 바꾸고, 답·만료된 요청의 사진을 지운다. {@link ExpiryJob}이 부른다. */
    public int expireAndCleanUp() {
        int expired = requests.expireDue(Instant.now(clock));
        int deleted = 0;
        for (String[] row : requests.photosToDelete(500)) {
            try {
                photos.delete(row[1]);
                requests.clearImageKey(row[0]);
                deleted++;
            } catch (RuntimeException e) {
                log.warn("사진 지우기 실패, 다음에 다시: request={} {}", row[0], e.toString());
            }
        }
        if (expired > 0 || deleted > 0) {
            log.info("만료 {}건, 사진 삭제 {}건", expired, deleted);
        }
        return expired;
    }

    // ---------- 도움 함수 ----------

    byte[] decodeJpeg(String imageBase64) {
        String b64 = imageBase64 == null ? "" : imageBase64.strip();
        int comma = b64.indexOf(',');
        if (b64.startsWith("data:") && comma > 0) {
            b64 = b64.substring(comma + 1);
        }
        if (b64.isEmpty()) {
            throw new HelperException(HttpStatus.BAD_REQUEST, "no_image", "사진이 없어요.");
        }
        if ((long) b64.length() * 3 / 4 > props.maxImageBytes()) {
            throw new HelperException(HttpStatus.CONTENT_TOO_LARGE, "image_too_large", "사진이 6MB를 넘어요.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new HelperException(HttpStatus.BAD_REQUEST, "bad_image", "사진을 읽지 못했어요(base64가 아님).");
        }
        if (bytes.length < 3 || (bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8 || (bytes[2] & 0xFF) != 0xFF) {
            throw new HelperException(HttpStatus.BAD_REQUEST, "bad_image", "JPEG 사진만 보낼 수 있어요.");
        }
        return bytes;
    }

    private void deletePhotoQuietly(String key) {
        try {
            photos.delete(key);
        } catch (RuntimeException e) {
            log.warn("사진 지우기 실패(수명 주기 규칙으로 24시간 안에 지워짐): {}", e.toString());
        }
    }

    static String seoul(Instant instant) {
        return instant == null ? null
                : OffsetDateTime.ofInstant(instant.truncatedTo(ChronoUnit.SECONDS), SEOUL).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
