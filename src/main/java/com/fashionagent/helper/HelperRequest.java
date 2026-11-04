package com.fashionagent.helper;

import java.time.Instant;
import java.util.UUID;

/** helper.requests 한 행. contactName은 contacts와 조인해서 채운다. */
public record HelperRequest(
        String id,
        UUID userId,
        String contactId,
        String contactName,
        String question,
        String imageKey,
        String token,
        Status status,
        String answer,
        Instant answeredAt,
        Instant expiresAt,
        Instant createdAt) {

    public enum Status {
        pending, answered, expired;
    }

    /** 만료 시각이 지났으면 DB가 아직 pending이어도 expired로 본다(스케줄러가 돌기 전 사이). */
    public Status statusAt(Instant now) {
        return status == Status.pending && !now.isBefore(expiresAt) ? Status.expired : status;
    }

    @Override
    public String toString() {
        return "HelperRequest[" + id + ", " + status + "]"; // 토큰·질문은 로그에 남기지 않음
    }
}
