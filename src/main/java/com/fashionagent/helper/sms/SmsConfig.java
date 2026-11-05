package com.fashionagent.helper.sms;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** SMS_PROVIDER로 문자 업체를 고른다: sens | nhncloud | solapi | 비우면 log(개발용, 보내지 않음). */
@Configuration
public class SmsConfig {

    private static final Logger log = LoggerFactory.getLogger(SmsConfig.class);

    @Bean
    SmsSender smsSender(
            RestClient.Builder builder,
            Clock clock,
            @Value("${helper.sms.provider:}") String provider,
            @Value("${helper.sms.api-key:}") String apiKey,
            @Value("${helper.sms.api-secret:}") String apiSecret,
            @Value("${helper.sms.sender:}") String sender,
            @Value("${helper.sms.service-id:}") String serviceId,
            @Value("${helper.sms.base-url:}") String baseUrl,
            @Value("${helper.sms.timeout:10s}") Duration timeout) {
        String p = provider.trim().toLowerCase();
        if (p.isEmpty() || p.equals("log")) {
            log.warn("SMS_PROVIDER가 비어 있어서 문자를 보내지 않고 로그에만 남깁니다(개발용).");
            return new LogSmsSender();
        }
        if (apiKey.isBlank() || apiSecret.isBlank() || sender.isBlank()) {
            throw new IllegalStateException("SMS_PROVIDER=" + p + "이면 SMS_API_KEY, SMS_API_SECRET, SMS_SENDER가 필요해요.");
        }
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        var http = builder.clone().requestFactory(requestFactory);
        String senderDigits = sender.replaceAll("\\D", "");
        if (p.equals("sens") && serviceId.isBlank()) {
            throw new IllegalStateException("SMS_PROVIDER=sens이면 SMS_SERVICE_ID(SENS 서비스 ID)도 필요해요.");
        }
        return switch (p) {
            case "sens" -> new SensSmsSender(http, or(baseUrl, "https://sens.apigw.ntruss.com"), apiKey, apiSecret, serviceId.trim(), senderDigits, clock);
            case "nhncloud" -> new NhnCloudSmsSender(http, or(baseUrl, "https://api-sms.cloud.toast.com"), apiKey, apiSecret, senderDigits);
            case "solapi" -> new SolapiSmsSender(http, or(baseUrl, "https://api.solapi.com"), apiKey, apiSecret, senderDigits, clock);
            default -> throw new IllegalStateException("SMS_PROVIDER는 sens, nhncloud, solapi 중 하나여야 해요: " + p);
        };
    }

    private static String or(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }
}
