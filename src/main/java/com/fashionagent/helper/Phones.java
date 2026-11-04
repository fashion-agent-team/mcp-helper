package com.fashionagent.helper;

import java.util.regex.Pattern;

/** 전화번호는 숫자만 저장한다(DB 주석과 같음). 국내 휴대폰 번호만 받는다(문자로 링크를 보내야 해서). */
final class Phones {

    private static final Pattern MOBILE = Pattern.compile("01[016789]\\d{7,8}");

    private Phones() {
    }

    static String normalize(String raw) {
        if (raw == null) {
            throw HelperException.badRequest("전화번호가 없어요.");
        }
        String digits = raw.replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("+82")) {
            digits = "0" + digits.substring(3);
        }
        if (!MOBILE.matcher(digits).matches()) {
            throw HelperException.badRequest("휴대폰 번호(010-0000-0000)만 등록할 수 있어요.");
        }
        return digits;
    }
}
