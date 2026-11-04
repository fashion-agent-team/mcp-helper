package com.fashionagent.helper;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/** id와 링크 토큰. 링크 토큰은 추측할 수 없어야 해서 SecureRandom 32바이트(256비트)를 쓴다. */
final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    private Ids() {
    }

    static String contactId() {
        return "h_" + random(9);
    }

    static String requestId() {
        return "r_" + random(9);
    }

    /** /h/&lt;token&gt; 링크용. 43자. */
    static String token() {
        return random(32);
    }

    private static String random(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return URL.encodeToString(buf);
    }

    /** userId는 게이트웨이가 준 UUID여야 한다(agent가 X-User-Id 값으로 덮어써서 넣음). */
    static UUID userId(String value) {
        if (value == null || value.isBlank()) {
            throw HelperException.badRequest("userId가 없어요.");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw HelperException.badRequest("userId는 UUID여야 해요.");
        }
    }
}
