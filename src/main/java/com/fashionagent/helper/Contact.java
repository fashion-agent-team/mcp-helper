package com.fashionagent.helper;

import java.util.UUID;

/** helper.contacts 한 행. phone은 문자 보낼 때만 쓰고 밖으로 내보내지 않는다. */
public record Contact(String id, UUID userId, String name, String phone, String relation) {

    /** API·도구 응답 모양. 전화번호 없음. */
    public record View(String contactId, String name, String relation) {
    }

    public View view() {
        return new View(id, name, relation);
    }

    @Override
    public String toString() {
        // 로그에 전화번호가 찍히지 않게
        return "Contact[" + id + ", " + name + "]";
    }
}
