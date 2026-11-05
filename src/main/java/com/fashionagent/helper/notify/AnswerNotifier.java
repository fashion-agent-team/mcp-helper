package com.fashionagent.helper.notify;

import com.fashionagent.helper.HelperRequest;

/** 가족이 답하면 사용자에게 알린다(약속 문서: FCM). 실패해도 답 저장은 그대로 둔다. */
public interface AnswerNotifier {

    void answered(HelperRequest request);
}
