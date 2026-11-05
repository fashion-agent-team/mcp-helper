package com.fashionagent.helper;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP 도구 3개 (앱 레포 docs/contracts/mcp-tools.md). 실패해도 예외를 던지지 않고 {"error": {"code", "message"}}를 돌려준다.
 * userId는 Gemini가 정하지 않고 agent가 X-User-Id 값으로 덮어써서 넣는다(약속 문서 3절).
 */
@Component
public class HelperTools {

    private static final Logger log = LoggerFactory.getLogger(HelperTools.class);

    private final HelperService service;

    public HelperTools(HelperService service) {
        this.service = service;
    }

    @Tool(name = "helper_contacts", description = """
            사용자가 등록해 둔 가족·친구 목록(contactId, 이름, 관계)을 돌려줍니다. 전화번호는 주지 않습니다. \
            사용자가 "엄마한테 물어봐 줘"처럼 사람에게 옷 확인을 부탁할 때, helper_ask에 넣을 contactId를 찾으려고 먼저 부르세요. \
            옷 추천 흐름에서 자동으로 부르지 않습니다.""")
    public Object helperContacts(@ToolParam(description = "사용자 id(UUID). agent가 채웁니다.") String userId) {
        return run(() -> Map.of("contacts", service.listContacts(Ids.userId(userId))));
    }

    @Tool(name = "helper_ask", description = """
            가족·친구에게 사진과 질문을 문자 링크로 보냅니다. 받은 사람은 앱 없이 링크를 열어 "괜찮아 / 얼룩 있어 / 직접 쓰기"로 답합니다. \
            사진은 24시간만 보관하고, 답을 받으면 바로 지웁니다. 사용자가 원할 때만 부르세요(예: "엄마한테 이 옷 괜찮은지 물어봐 줘"). \
            돌려준 requestId로 나중에 helper_answer를 부릅니다.""")
    public Object helperAsk(
            @ToolParam(description = "사용자 id(UUID). agent가 채웁니다.") String userId,
            @ToolParam(description = "helper_contacts에서 받은 contactId. 예: h_1") String contactId,
            @ToolParam(description = "JPEG 사진 base64. agent가 직전 사진을 넣습니다.") String imageBase64,
            @ToolParam(description = "받는 사람에게 보여 줄 질문. 200자 이내. 예: 이 옷 오늘 결혼식에 괜찮은지 봐 줄래?") String question) {
        return run(() -> service.ask(Ids.userId(userId), contactId, imageBase64, question));
    }

    @Tool(name = "helper_answer", description = """
            helper_ask로 보낸 요청에 답이 왔는지 확인합니다. status는 pending(아직), answered(답 옴), expired(24시간 지남). \
            answered면 answer(답 내용)와 answeredBy(답한 사람)를 읽어 주세요. 사용자가 "답 왔어?"라고 물을 때 부르세요.""")
    public Object helperAnswer(
            @ToolParam(description = "사용자 id(UUID). agent가 채웁니다.") String userId,
            @ToolParam(description = "helper_ask가 돌려준 requestId. 예: r_9") String requestId) {
        return run(() -> service.answerOf(Ids.userId(userId), requestId));
    }

    private Object run(java.util.function.Supplier<Object> body) {
        try {
            return body.get();
        } catch (HelperException e) {
            return error(e.code(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("helper 도구 실패: {}", e.toString());
            return error("upstream_failed", "지금은 처리하지 못했어요.");
        }
    }

    static Map<String, Object> error(String code, String message) {
        return Map.of("error", Map.of("code", code, "message", message));
    }
}
