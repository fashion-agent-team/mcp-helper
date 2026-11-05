package com.fashionagent.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 10분마다 만료 처리와 사진 삭제. R2 수명 주기 규칙(24시간)은 이게 멈췄을 때의 안전망이다. */
@Component
public class ExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiryJob.class);

    private final HelperService service;

    public ExpiryJob(HelperService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${helper.expiry-interval:10m}", initialDelayString = "${helper.expiry-initial-delay:1m}")
    public void run() {
        try {
            service.expireAndCleanUp();
        } catch (RuntimeException e) {
            log.warn("만료 처리 실패: {}", e.toString());
        }
    }
}
