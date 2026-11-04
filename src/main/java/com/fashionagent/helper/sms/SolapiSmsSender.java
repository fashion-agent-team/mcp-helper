package com.fashionagent.helper.sms;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 솔라피 메시지 API v4 (/messages/v4/send). 인증은 HMAC-SHA256(date + salt).
 * type을 비워 두면 솔라피가 길이를 보고 SMS·LMS를 고른다.
 */
public class SolapiSmsSender implements SmsSender {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestClient http;
    private final String apiKey;
    private final String apiSecret;
    private final String sender;
    private final Clock clock;

    public SolapiSmsSender(RestClient.Builder builder, String baseUrl, String apiKey, String apiSecret, String sender, Clock clock) {
        this.http = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.sender = sender;
        this.clock = clock;
    }

    @Override
    public void send(String to, String text) {
        try {
            http.post()
                    .uri("/messages/v4/send")
                    .header("Authorization", authorization())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", Map.of("to", to, "from", sender, "text", text)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new SmsException("솔라피 호출 실패: " + e.getClass().getSimpleName(), e);
        }
    }

    String authorization() {
        String date = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString();
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt + ", signature=" + sign(apiSecret, date + salt);
    }

    static String sign(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
