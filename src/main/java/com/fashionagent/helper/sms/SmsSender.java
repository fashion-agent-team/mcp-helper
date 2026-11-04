package com.fashionagent.helper.sms;

import java.nio.charset.Charset;

/** 문자 한 통 보내기. 실패하면 {@link SmsException}. */
public interface SmsSender {

    /** 국내 문자 기준: EUC-KR 90바이트(한글 45자)까지 SMS, 넘으면 LMS. */
    int SMS_MAX_BYTES = 90;

    Charset EUC_KR = Charset.forName("EUC-KR");

    void send(String to, String text);

    static boolean isLong(String text) {
        return text.getBytes(EUC_KR).length > SMS_MAX_BYTES;
    }

    class SmsException extends RuntimeException {
        public SmsException(String message) {
            super(message);
        }

        public SmsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
