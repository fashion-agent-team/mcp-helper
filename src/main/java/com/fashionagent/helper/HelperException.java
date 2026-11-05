package com.fashionagent.helper;

import org.springframework.http.HttpStatus;

/** 약속 문서 4절의 오류. MCP 도구는 {"error": …}로, REST는 같은 모양의 본문과 상태 코드로 바꾼다. */
public class HelperException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public HelperException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static HelperException badRequest(String message) {
        return new HelperException(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    public static HelperException notFound(String message) {
        return new HelperException(HttpStatus.NOT_FOUND, "not_found", message);
    }

    public static HelperException upstreamFailed(String message) {
        return new HelperException(HttpStatus.BAD_GATEWAY, "upstream_failed", message);
    }
}
