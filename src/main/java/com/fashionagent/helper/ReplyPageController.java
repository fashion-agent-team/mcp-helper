package com.fashionagent.helper;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 가족·친구가 문자 링크로 여는 답장 페이지. 로그인 없이 링크의 토큰(256비트)으로만 연다.
 * 게이트웨이가 /h/** 를 그대로 넘겨 준다(약속 문서 2절).
 */
@Controller
public class ReplyPageController {

    /** 버튼 값 → 저장할 답. "직접 쓰기"는 text를 그대로 저장한다. */
    static final String OK = "괜찮아요.";
    static final String STAIN = "얼룩이 있어요.";

    private final HelperService service;
    private final Clock clock;

    public ReplyPageController(HelperService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    /** 링크가 검색엔진·다른 사이트로 새지 않게, 캐시되지 않게. */
    @ModelAttribute
    void privateHeaders(HttpServletResponse res) {
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("X-Robots-Tag", "noindex, nofollow");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; form-action 'self'");
    }

    @GetMapping("/h/{token}")
    public String page(@PathVariable String token, @RequestParam(required = false) String done, Model model, HttpServletResponse res) {
        var found = service.byToken(token);
        if (found.isEmpty()) {
            res.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("state", "notfound");
            return "reply";
        }
        var r = found.get();
        var status = r.statusAt(Instant.now(clock));
        model.addAttribute("token", token);
        model.addAttribute("question", r.question());
        model.addAttribute("state", switch (status) {
            case pending -> "pending";
            case answered -> done != null ? "thanks" : "answered";
            case expired -> "expired";
        });
        return "reply";
    }

    @GetMapping("/h/{token}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable String token) {
        return service.photoOf(token)
                .map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(bytes))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.GONE).build());
    }

    @PostMapping("/h/{token}")
    public String answer(@PathVariable String token, @RequestParam(required = false) String choice,
            @RequestParam(required = false) String text, Model model, HttpServletResponse res) {
        String answer = switch (choice == null ? "" : choice) {
            case "ok" -> OK;
            case "stain" -> STAIN;
            case "text" -> text;
            default -> null;
        };
        if (answer == null || answer.isBlank()) {
            return page(token, null, model.addAttribute("needAnswer", true), res);
        }
        service.submitAnswer(token, answer);
        // 이미 답했거나 만료됐어도 GET에서 맞는 안내를 보여 준다
        return "redirect:/h/" + token + "?done=1";
    }
}
