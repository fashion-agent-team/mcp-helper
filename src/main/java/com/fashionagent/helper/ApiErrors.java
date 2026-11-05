package com.fashionagent.helper;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** REST 오류를 약속 문서 4절 모양 {"error": {"code", "message"}}로 바꾼다. */
@RestControllerAdvice(assignableTypes = ContactController.class)
public class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(HelperException.class)
    ResponseEntity<Map<String, Object>> helper(HelperException e) {
        return ResponseEntity.status(e.status()).body(HelperTools.error(e.code(), e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(HelperTools.error("bad_request", "JSON 본문을 읽지 못했어요."));
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Map<String, Object>> other(RuntimeException e) {
        log.warn("contacts API 실패: {}", e.toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HelperTools.error("internal", "서버 오류예요."));
    }
}
