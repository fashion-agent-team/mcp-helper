package com.fashionagent.helper.notify;

import com.fashionagent.helper.HelperRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 지금은 로그만 남긴다. FCM 푸시 토큰은 gateway의 account.push_tokens에 있어서
 * 이 서버가 직접 읽을 수 없다(약속 문서 7절). gateway에 "사용자에게 푸시 보내기" 내부 API가 생기면 거기로 바꾼다.
 * 그 전에는 agent가 helper_answer로 확인해서 알려 준다.
 */
@Component
public class LogAnswerNotifier implements AnswerNotifier {

    private static final Logger log = LoggerFactory.getLogger(LogAnswerNotifier.class);

    @Override
    public void answered(HelperRequest request) {
        log.info("답 도착: request={} (푸시 알림은 아직 연결 안 됨)", request.id());
    }
}
