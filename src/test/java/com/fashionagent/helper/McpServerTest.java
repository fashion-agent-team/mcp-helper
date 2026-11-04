package com.fashionagent.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fashionagent.helper.photo.MemoryPhotoStore;
import com.fashionagent.helper.photo.PhotoStore;
import com.fashionagent.helper.sms.SmsSender;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제로 서버와 PostgreSQL을 띄우고 MCP 클라이언트로 /mcp에 붙어 본다(agent가 붙는 방식과 같음).
 * Docker가 없으면 건너뛴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"helper.public-base-url=https://fa.test", "helper.ask-limit-per-hour=3", "helper.expiry-initial-delay=1h"})
@Import(TestDb.class)
@Testcontainers(disabledWithoutDocker = true)
class McpServerTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
    static final Pattern LINK = Pattern.compile("https://fa\\.test/h/([A-Za-z0-9_-]{43})");

    @LocalServerPort
    int port;

    @MockitoBean
    SmsSender sms;

    @Autowired
    PhotoStore photos;

    @Autowired
    TestDb.MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    McpSyncClient client;
    RestClient http;
    String user;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM helper.requests").update();
        jdbc.sql("DELETE FROM helper.contacts").update();
        clock.set(Instant.parse("2026-11-04T05:00:00Z"));
        user = UUID.randomUUID().toString();
        http = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp").build();
        client = McpClient.sync(transport).build();
        client.initialize();
    }

    @AfterEach
    void close() {
        client.closeGracefully();
    }

    @Test
    @DisplayName("tools list에 helper 도구 3개가 있다")
    @SuppressWarnings("unchecked")
    void toolsList() {
        var tools = client.listTools().tools();
        assertThat(tools).extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder("helper_contacts", "helper_ask", "helper_answer");
        var ask = tools.stream().filter(t -> t.name().equals("helper_ask")).findFirst().orElseThrow();
        assertThat((List<String>) ask.inputSchema().get("required")).contains("userId", "contactId", "imageBase64", "question");
    }

    @Test
    @DisplayName("연락처 API: 추가·목록·삭제, 응답에 전화번호 없음")
    void contactsApi() {
        var created = http.post().uri("/api/v1/me/contacts").header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "엄마", "phone", "010-1234-5678", "relation", "가족")).retrieve().toEntity(String.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).contains("\"name\":\"엄마\"", "\"contactId\":\"h_").doesNotContain("010", "phone");

        var dup = http.post().uri("/api/v1/me/contacts").header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "엄마2", "phone", "01012345678")).retrieve().toEntity(String.class);
        assertThat(dup.getStatusCode().value()).isEqualTo(400);
        assertThat(dup.getBody()).contains("\"code\":\"bad_request\"");

        var list = http.get().uri("/api/v1/me/contacts").header("X-User-Id", user).retrieve().body(String.class);
        assertThat(list).contains("엄마").doesNotContain("01012345678");
        // 다른 사용자에게는 안 보인다
        var other = http.get().uri("/api/v1/me/contacts").header("X-User-Id", UUID.randomUUID().toString()).retrieve().body(String.class);
        assertThat(other).isEqualTo("{\"contacts\":[]}");

        String id = jdbc.sql("SELECT id FROM helper.contacts").query(String.class).single();
        var del = http.delete().uri("/api/v1/me/contacts/" + id).header("X-User-Id", user).retrieve().toBodilessEntity();
        assertThat(del.getStatusCode().value()).isEqualTo(204);
        var again = http.delete().uri("/api/v1/me/contacts/" + id).header("X-User-Id", user).retrieve().toBodilessEntity();
        assertThat(again.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("X-User-Id가 없으면 401")
    void contactsNeedUser() {
        var res = http.get().uri("/api/v1/me/contacts").retrieve().toEntity(String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(res.getBody()).contains("\"code\":\"unauthorized\"");
    }

    @Test
    @DisplayName("묻기 → 문자 링크 → 가족이 답 → helper_answer answered, 사진은 바로 지움")
    void askAndAnswer() {
        String contactId = addContact("엄마", "010-1111-2222");

        assertThat(text(call("helper_contacts", Map.of("userId", user))))
                .contains("\"contactId\":\"" + contactId + "\"", "\"name\":\"엄마\"").doesNotContain("0101111");

        String asked = text(call("helper_ask", Map.of("userId", user, "contactId", contactId,
                "imageBase64", Base64.getEncoder().encodeToString(JPEG), "question", "이 옷 오늘 결혼식에 괜찮은지 봐 줄래?")));
        assertThat(asked).contains("\"requestId\":\"r_", "\"sentTo\":\"엄마\"", "\"expiresAt\":\"2026-11-05T14:00:00+09:00\"");
        String requestId = asked.replaceAll(".*\"requestId\":\"(r_[^\"]+)\".*", "$1");

        var textCaptor = ArgumentCaptor.forClass(String.class);
        verify(sms).send(eq("01011112222"), textCaptor.capture());
        var m = LINK.matcher(textCaptor.getValue());
        assertThat(m.find()).isTrue();
        String token = m.group(1);
        assertThat(textCaptor.getValue()).contains("이 옷 오늘 결혼식에 괜찮은지 봐 줄래?");

        assertThat(text(call("helper_answer", Map.of("userId", user, "requestId", requestId))))
                .contains("\"status\":\"pending\"", "\"answer\":null");

        // 가족이 링크를 연다
        var page = http.get().uri("/h/" + token).retrieve().toEntity(String.class);
        assertThat(page.getStatusCode().value()).isEqualTo(200);
        assertThat(page.getBody()).contains("이 옷 오늘 결혼식에 괜찮은지 봐 줄래?", "괜찮아", "얼룩 있어", "직접 쓰기");
        assertThat(page.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(page.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        assertThat(http.get().uri("/h/" + token + "/photo").retrieve().body(byte[].class)).isEqualTo(JPEG);

        // 직접 쓰기로 답한다
        var form = new LinkedMultiValueMap<String, String>();
        form.add("choice", "text");
        form.add("text", "얼룩 없어, 그림자야.");
        var posted = http.post().uri("/h/" + token).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
        assertThat(posted.getStatusCode().is3xxRedirection()).isTrue();

        assertThat(text(call("helper_answer", Map.of("userId", user, "requestId", requestId))))
                .contains("\"status\":\"answered\"", "\"answer\":\"얼룩 없어, 그림자야.\"", "\"answeredBy\":\"엄마\"",
                        "\"answeredAt\":\"2026-11-04T14:00:00+09:00\"");
        assertThat(((MemoryPhotoStore) photos).size()).isZero();
        assertThat(http.get().uri("/h/" + token + "/photo").retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(410);
        assertThat(http.get().uri("/h/" + token).retrieve().body(String.class)).contains("이미 답한 요청");

        // 두 번째 답은 저장되지 않는다
        form.set("choice", "stain");
        http.post().uri("/h/" + token).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
        assertThat(text(call("helper_answer", Map.of("userId", user, "requestId", requestId)))).contains("그림자야");

        // 다른 사용자는 이 요청을 못 본다
        assertThat(text(call("helper_answer", Map.of("userId", UUID.randomUUID().toString(), "requestId", requestId))))
                .contains("\"code\":\"not_found\"");
    }

    @Test
    @DisplayName("24시간이 지나면 expired, 사진 삭제, 답 못 함")
    void expires() {
        String contactId = addContact("친구", "01033334444");
        String asked = text(call("helper_ask", Map.of("userId", user, "contactId", contactId,
                "imageBase64", Base64.getEncoder().encodeToString(JPEG), "question", "괜찮아?")));
        String requestId = asked.replaceAll(".*\"requestId\":\"(r_[^\"]+)\".*", "$1");
        var textCaptor = ArgumentCaptor.forClass(String.class);
        verify(sms).send(anyString(), textCaptor.capture());
        var m = LINK.matcher(textCaptor.getValue());
        assertThat(m.find()).isTrue();

        clock.advance(Duration.ofHours(24));
        assertThat(text(call("helper_answer", Map.of("userId", user, "requestId", requestId)))).contains("\"status\":\"expired\"");
        assertThat(http.get().uri("/h/" + m.group(1)).retrieve().body(String.class)).contains("시간이 지났어요");

        var form = new LinkedMultiValueMap<String, String>();
        form.add("choice", "ok");
        http.post().uri("/h/" + m.group(1)).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();

        assertThat(((MemoryPhotoStore) photos).size()).isEqualTo(1);
        new ExpiryJob(serviceBean()).run();
        assertThat(((MemoryPhotoStore) photos).size()).isZero();
        assertThat(jdbc.sql("SELECT status FROM helper.requests").query(String.class).single()).isEqualTo("expired");
        assertThat(jdbc.sql("SELECT image_key FROM helper.requests").query(String.class).optional()).isEmpty();
    }

    @Test
    @DisplayName("잘못된 입력은 error JSON")
    void errors() {
        String contactId = addContact("엄마", "01055556666");
        String jpeg = Base64.getEncoder().encodeToString(JPEG);
        assertThat(text(call("helper_contacts", Map.of("userId", "nope")))).contains("\"code\":\"bad_request\"");
        assertThat(text(call("helper_ask", Map.of("userId", user, "contactId", "h_nobody", "imageBase64", jpeg, "question", "q"))))
                .contains("\"code\":\"not_found\"");
        assertThat(text(call("helper_ask", Map.of("userId", user, "contactId", contactId, "imageBase64", "", "question", "q"))))
                .contains("\"code\":\"no_image\"");
        assertThat(text(call("helper_ask", Map.of("userId", user, "contactId", contactId,
                "imageBase64", Base64.getEncoder().encodeToString("PNG!".getBytes()), "question", "q"))))
                .contains("\"code\":\"bad_image\"");
        assertThat(text(call("helper_ask", Map.of("userId", user, "contactId", contactId, "imageBase64", jpeg, "question", " "))))
                .contains("\"code\":\"bad_request\"");
        assertThat(text(call("helper_answer", Map.of("userId", user, "requestId", "r_nope")))).contains("\"code\":\"not_found\"");
    }

    @Test
    @DisplayName("문자가 실패하면 upstream failed, 요청과 사진을 남기지 않는다")
    void smsFailure() {
        String contactId = addContact("엄마", "01077778888");
        doThrow(new SmsSender.SmsException("down")).when(sms).send(anyString(), anyString());
        assertThat(text(call("helper_ask", Map.of("userId", user, "contactId", contactId,
                "imageBase64", Base64.getEncoder().encodeToString(JPEG), "question", "q"))))
                .contains("\"code\":\"upstream_failed\"");
        assertThat(jdbc.sql("SELECT count(*) FROM helper.requests").query(Integer.class).single()).isZero();
        assertThat(((MemoryPhotoStore) photos).size()).isZero();
    }

    @Test
    @DisplayName("한 시간에 정한 횟수를 넘으면 rate limited")
    void rateLimited() {
        String contactId = addContact("엄마", "01099990000");
        var args = Map.<String, Object>of("userId", user, "contactId", contactId,
                "imageBase64", Base64.getEncoder().encodeToString(JPEG), "question", "q");
        for (int i = 0; i < 3; i++) {
            assertThat(text(call("helper_ask", args))).contains("requestId");
        }
        assertThat(text(call("helper_ask", args))).contains("\"code\":\"rate_limited\"");
        clock.advance(Duration.ofMinutes(61));
        assertThat(text(call("helper_ask", args))).contains("requestId");
    }

    @Test
    @DisplayName("없는 링크는 404 페이지, actuator health는 UP")
    void unknownLinkAndHealth() {
        var res = http.get().uri("/h/" + "a".repeat(43)).retrieve().toEntity(String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(res.getBody()).contains("없는 링크");
        assertThat(http.get().uri("/actuator/health").retrieve().body(String.class)).contains("UP");
    }

    @Autowired
    HelperService service;

    private HelperService serviceBean() {
        return service;
    }

    private String addContact(String name, String phone) {
        String body = http.post().uri("/api/v1/me/contacts").header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "phone", phone)).retrieve().body(String.class);
        return body.replaceAll(".*\"contactId\":\"(h_[^\"]+)\".*", "$1");
    }

    private McpSchema.CallToolResult call(String tool, Map<String, Object> args) {
        return client.callTool(new McpSchema.CallToolRequest(tool, args));
    }

    private static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }
}
