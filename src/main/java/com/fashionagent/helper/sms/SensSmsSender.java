package com.fashionagent.helper.sms;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 네이버 클라우드 SENS SMS v2. SMS_API_KEY = Access Key, SMS_API_SECRET = Secret Key,
 * SMS_SERVICE_ID = SENS 프로젝트의 서비스 ID(ncp:sms:kr:…), SMS_SENDER = 등록한 발신 번호.
 * 서명: Base64(HmacSHA256(secret, "POST {uri}\n{timestamp}\n{accessKey}")).
 */
public class SensSmsSender implements SmsSender {

    private final RestClient http;
    private final String accessKey;
    private final String secretKey;
    private final String serviceId;
    private final String sender;
    private final Clock clock;

    public SensSmsSender(RestClient.Builder builder, String baseUrl, String accessKey, String secretKey, String serviceId,
            String sender, Clock clock) {
        this.http = builder.baseUrl(baseUrl).build();
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.serviceId = serviceId;
        this.sender = sender;
        this.clock = clock;
    }

    @Override
    public void send(String to, String text) {
        String uri = "/sms/v2/services/" + serviceId + "/messages";
        String timestamp = Long.toString(clock.millis());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", SmsSender.isLong(text) ? "LMS" : "SMS");
        body.put("from", sender);
        body.put("content", text);
        body.put("messages", List.of(Map.of("to", to)));
        try {
            http.post()
                    .uri(uri)
                    .header("x-ncp-apigw-timestamp", timestamp)
                    .header("x-ncp-iam-access-key", accessKey)
                    .header("x-ncp-apigw-signature-v2", sign(secretKey, "POST " + uri + "\n" + timestamp + "\n" + accessKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new SmsException("SENS 호출 실패: " + e.getClass().getSimpleName(), e);
        }
    }

    static String sign(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
