package com.fashionagent.helper;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * helper 설정 (application.yml의 helper.*).
 *
 * @param publicBaseUrl 가족이 여는 링크의 도메인. 예: https://fashion-agent.example.com (HELPER_PUBLIC_BASE_URL)
 * @param requestTtl    요청과 사진을 두는 시간. 약속 문서대로 24시간
 * @param askLimitPerHour 사용자 한 명이 한 시간에 보낼 수 있는 요청 수 (문자 요금·스팸 방지)
 * @param maxContacts   사용자 한 명이 등록할 수 있는 가족·친구 수
 * @param maxImageBytes 사진 한 장 최대 크기. 약속 문서대로 약 6MB
 * @param maxQuestionLength 질문 최대 글자 수 (문자 한 통에 들어가게)
 */
@ConfigurationProperties("helper")
public record HelperProperties(
        @DefaultValue("http://localhost:8096") String publicBaseUrl,
        @DefaultValue("24h") Duration requestTtl,
        @DefaultValue("10") int askLimitPerHour,
        @DefaultValue("20") int maxContacts,
        @DefaultValue("6291456") int maxImageBytes,
        @DefaultValue("200") int maxQuestionLength) {

    /** 끝의 / 를 떼고 돌려준다. */
    public String baseUrl() {
        return publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }
}
