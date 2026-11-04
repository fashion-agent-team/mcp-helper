package com.fashionagent.helper.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 문자 업체 API 모양 확인(실제로 보내지 않음). 요청 모양은 각 업체 문서 기준. */
class SmsSendersTest {

    static final String OK = """
            {"header":{"isSuccessful":true,"resultCode":0,"resultMessage":"SUCCESS"},
             "body":{"data":{"requestId":"r1","sendResultList":[{"recipientNo":"01012345678","resultCode":0,"resultMessage":"SUCCESS"}]}}}""";

    @Test
    @DisplayName("NHN Cloud: 짧으면 SMS, 길면 LMS(mms)로 보낸다")
    void nhnShortAndLong() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sender = new NhnCloudSmsSender(builder, "https://nhn.test", "APPKEY", "SECRET", "0212345678");

        server.expect(requestTo("https://nhn.test/sms/v3.0/appKeys/APPKEY/sender/sms"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Secret-Key", "SECRET"))
                .andExpect(jsonPath("$.sendNo").value("0212345678"))
                .andExpect(jsonPath("$.recipientList[0].recipientNo").value("01012345678"))
                .andExpect(jsonPath("$.title").doesNotExist())
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://nhn.test/sms/v3.0/appKeys/APPKEY/sender/mms"))
                .andExpect(jsonPath("$.title").value("패션 에이전트"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        sender.send("01012345678", "짧은 문자");
        sender.send("01012345678", "가".repeat(46)); // 92바이트 > 90
        server.verify();
    }

    @Test
    @DisplayName("NHN Cloud: isSuccessful false면 실패")
    void nhnFailure() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sender = new NhnCloudSmsSender(builder, "https://nhn.test", "APPKEY", "SECRET", "0212345678");
        server.expect(requestTo("https://nhn.test/sms/v3.0/appKeys/APPKEY/sender/sms"))
                .andRespond(withSuccess("""
                        {"header":{"isSuccessful":false,"resultCode":-1000,"resultMessage":"Invalid sendNo"}}""", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> sender.send("01012345678", "hi")).isInstanceOf(SmsSender.SmsException.class)
                .hasMessageContaining("Invalid sendNo");
    }

    @Test
    @DisplayName("솔라피: HMAC 인증 헤더와 메시지 본문")
    void solapi() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var clock = Clock.fixed(Instant.parse("2026-11-04T05:00:00Z"), ZoneOffset.UTC);
        var sender = new SolapiSmsSender(builder, "https://solapi.test", "KEY", "SECRET", "0212345678", clock);
        server.expect(requestTo("https://solapi.test/messages/v4/send"))
                .andExpect(header("Authorization", org.hamcrest.Matchers.matchesPattern(
                        "HMAC-SHA256 apiKey=KEY, date=2026-11-04T05:00:00Z, salt=[0-9a-f]{32}, signature=[0-9a-f]{64}")))
                .andExpect(jsonPath("$.message.to").value("01012345678"))
                .andExpect(jsonPath("$.message.from").value("0212345678"))
                .andExpect(jsonPath("$.message.text").value("안녕"))
                .andRespond(withSuccess("{\"statusCode\":\"2000\"}", MediaType.APPLICATION_JSON));
        sender.send("01012345678", "안녕");
        server.verify();
    }

    @Test
    @DisplayName("솔라피: 서명은 HMAC-SHA256(secret, date+salt)의 hex")
    void solapiSignature() {
        // echo -n "2026-11-04T05:00:00Zabc" | openssl dgst -sha256 -hmac SECRET
        assertThat(SolapiSmsSender.sign("SECRET", "2026-11-04T05:00:00Zabc"))
                .isEqualTo("2723e8b1fee11302eb3121f8d004b7c35bf1465871ea6543ff79d6be129214e1");
    }

    @Test
    @DisplayName("솔라피: 4xx면 실패")
    void solapiFailure() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sender = new SolapiSmsSender(builder, "https://solapi.test", "KEY", "SECRET", "0212345678", Clock.systemUTC());
        server.expect(requestTo("https://solapi.test/messages/v4/send"))
                .andRespond(withBadRequest().body("{\"errorCode\":\"ValidationError\"}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> sender.send("01012345678", "hi")).isInstanceOf(SmsSender.SmsException.class);
    }
}
