package com.fashionagent.helper.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 개발용. 문자를 보내지 않고 내용만 로그에 남긴다(전화번호는 남기지 않음). 로그의 링크를 열어 답장 페이지를 시험할 수 있다. */
public class LogSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public void send(String to, String text) {
        log.info("[개발용 문자, 실제로 보내지 않음] {}", text);
    }
}
