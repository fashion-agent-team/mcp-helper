package com.fashionagent.helper.sms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * NHN Cloud SMS v3.0. SMS_API_KEY = 앱키, SMS_API_SECRET = 시크릿 키, SMS_SENDER = 사전 등록한 발신 번호.
 * 긴 문자는 LMS(/sender/mms)로 보낸다.
 */
public class NhnCloudSmsSender implements SmsSender {

    static final String TITLE = "패션 에이전트";

    private final RestClient http;
    private final String appKey;
    private final String sender;

    public NhnCloudSmsSender(RestClient.Builder builder, String baseUrl, String appKey, String secretKey, String sender) {
        this.http = builder.baseUrl(baseUrl).defaultHeader("X-Secret-Key", secretKey).build();
        this.appKey = appKey;
        this.sender = sender;
    }

    @Override
    public void send(String to, String text) {
        boolean lms = SmsSender.isLong(text);
        Map<String, Object> body = new LinkedHashMap<>();
        if (lms) {
            body.put("title", TITLE);
        }
        body.put("body", text);
        body.put("sendNo", sender);
        body.put("recipientList", List.of(Map.of("recipientNo", to)));
        Response res;
        try {
            res = http.post()
                    .uri("/sms/v3.0/appKeys/{appKey}/sender/{kind}", appKey, lms ? "mms" : "sms")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Response.class);
        } catch (RestClientException e) {
            throw new SmsException("NHN Cloud 호출 실패: " + e.getClass().getSimpleName(), e);
        }
        if (res == null || res.header() == null || !res.header().isSuccessful()) {
            throw new SmsException("NHN Cloud 실패: " + (res == null || res.header() == null ? "응답 없음" : res.header().resultMessage()));
        }
        var results = res.body() == null || res.body().data() == null ? null : res.body().data().sendResultList();
        if (results != null) {
            for (var r : results) {
                if (r.resultCode() != 0) {
                    throw new SmsException("NHN Cloud 수신자 실패: " + r.resultCode() + " " + r.resultMessage());
                }
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Header(boolean isSuccessful, int resultCode, String resultMessage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(String requestId, List<SendResult> sendResultList) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SendResult(String recipientNo, int resultCode, String resultMessage) {
    }
}
