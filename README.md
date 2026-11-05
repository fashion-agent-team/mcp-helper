# mcp-helper

**helper MCP 서버 · 가족·친구에게 확인 요청 (개별 기능)** · 패션 에이전트(시각장애인용 음성 AI 옷 추천)의 한 부분입니다.

담당: 박창근

## 한눈에

- 포트: 8096, 엔드포인트: `http://mcp-helper:8096/mcp` (Streamable HTTP)
- 기술: Spring Boot, Spring AI MCP Server, 문자(네이버 클라우드 SENS, NHN Cloud·솔라피로도 바꿀 수 있음), 답장용 웹 페이지(`/h/<token>`), Cloudflare R2(사진 24시간), FCM, PostgreSQL
- 상태 확인: `GET /actuator/health`

## MCP 도구

| 도구 | 하는 일 |
| --- | --- |
| `helper_contacts` | 등록한 가족·친구 목록 |
| `helper_ask` | 사진과 질문을 문자 링크로 보내기 |
| `helper_answer` | 답이 왔는지 확인 |

입출력 JSON은 [MCP 도구 입출력](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/mcp-tools.md)를 그대로 따릅니다.

## 이 레포가 지킬 것

- **개별 기능**입니다. 사용자가 "엄마한테 물어봐 줘"라고 할 때만 씁니다. 옷 추천 중에 자동으로 부르지 않습니다.
- 사진은 R2에 24시간만 두고 자동 삭제합니다(수명 주기 규칙). 이 서비스가 유일한 사진 보관 예외입니다.
- 가족은 앱 없이 문자 링크의 웹 페이지에서 "괜찮아 / 얼룩 있어 / 직접 쓰기"로 답합니다. 답을 받으면 사진은 바로 지웁니다. 사용자에게 보내는 FCM 알림은 gateway가 생긴 뒤에 붙이고, 그 전에는 agent가 `helper_answer`로 확인합니다.
- 연락처 API(`/api/v1/me/contacts`)도 엽니다(게이트웨이 경유). 응답에 전화번호는 내보내지 않습니다.
- DB 스키마 `helper`만 씁니다(`contacts`, `requests`).

## 할 일

- [x] 뼈대 + `/mcp` + Dockerfile
- [x] 연락처 API, `helper_contacts`
- [x] `helper_ask`(R2 업로드 + 문자: SENS·NHN Cloud·솔라피, `SMS_PROVIDER`로 고름)
- [x] 답장 웹 페이지, `helper_answer`, 24시간 만료·사진 삭제
- [ ] SENS 가입·발신 번호 등록, 실제로 보내 보기
- [ ] FCM 알림 (gateway의 푸시 토큰이 필요, [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) "남은 것")

실행·설정·기술 선택 측정은 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)에 있습니다.

## 공통 약속

모든 레포가 지키는 약속은 앱 레포에 있습니다. **코드보다 약속 문서를 먼저 고칩니다.**

- [서비스 간 약속](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/README.md): 공통 규칙, 포트, 인증(`X-User-Id`), 오류 모양, DB 스키마, 옷 추천 흐름
- [MCP 도구 입출력](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/mcp-tools.md)
- [앱 ↔ 게이트웨이 API (OpenAPI)](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/openapi.yaml)
- [아키텍처와 기술 스택](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/ARCHITECTURE.md)

## 버전

| 항목 | 버전 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.0.9 |
| Spring Cloud | 2025.1.4 |
| Spring AI (BOM) | 2.0.2 |

## 브랜치

GitFlow를 따릅니다. `develop`에서 `feature/…` 브랜치를 따서 PR로 합칩니다. 규칙은 앱 레포의 `docs/GITFLOW.md`를 봅니다.

## 비밀 값

API 키와 DB 비밀번호는 레포에 넣지 않습니다. 로컬은 `.env`(커밋 금지), 배포는 서버 환경 변수로 둡니다. 목록은 앱 레포 `docs/infra/.env.example`에 있습니다.
