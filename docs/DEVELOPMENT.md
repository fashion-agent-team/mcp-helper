# 개발 방법

## 필요한 것

- Java 21 (Gradle은 `./gradlew`가 받아 옵니다)
- PostgreSQL 16 (스키마 `helper`는 Flyway가 만듭니다)
- Docker: 테스트의 DB 부분(Testcontainers)에 씁니다. Docker가 없으면 그 테스트만 건너뜁니다.
- 키 없이도 돌아갑니다. 문자 업체·R2 값이 비어 있으면 문자는 로그에만 남기고 사진은 메모리에 둡니다(개발용).

## 실행

```bash
docker run -d --name pg -e POSTGRES_DB=fashion -e POSTGRES_USER=fashion -e POSTGRES_PASSWORD=pw -p 5432:5432 postgres:16-alpine
SPRING_DATASOURCE_PASSWORD=pw ./gradlew bootRun     # http://localhost:8096
./gradlew test                                      # 단위 테스트 + 실제 DB·/mcp 접속 테스트
```

- MCP 엔드포인트: `http://localhost:8096/mcp` (Streamable HTTP)
- 상태 확인: `curl localhost:8096/actuator/health`
- 가족 답장 페이지: `http://localhost:8096/h/<token>` (개발용 문자 로그에 링크가 찍힙니다)

## 손으로 불러 보기

```bash
U=11111111-1111-1111-1111-111111111111
curl -s -X POST localhost:8096/api/v1/me/contacts -H "X-User-Id: $U" -H 'Content-Type: application/json' \
  -d '{"name":"엄마","phone":"010-1234-5678","relation":"가족"}'
npx @modelcontextprotocol/inspector
# Transport: Streamable HTTP, URL: http://localhost:8096/mcp
# helper_contacts(userId) → helper_ask(userId, contactId, imageBase64, question) → 로그의 링크 열고 답하기 → helper_answer(userId, requestId)
```

## Docker

```bash
docker build -t ghcr.io/fashion-agent-team/mcp-helper:dev .
```

## 흐름

```
agent ─helper_ask─▶ 사진을 R2에 올림 → helper.requests 저장(pending, 24시간) → 문자로 https://<도메인>/h/<token>
가족  ─/h/<token>─▶ 사진·질문 보고 "괜찮아 / 얼룩 있어 / 직접 쓰기" → answered 저장 → 사진 바로 삭제 → 알림(지금은 로그)
agent ─helper_answer─▶ pending | answered(answer, answeredBy) | expired
10분마다 ExpiryJob: 24시간 지난 pending → expired, 남은 사진 삭제. R2 수명 주기 규칙(1일)은 안전망
```

## 코드 구성

| 파일 | 하는 일 |
| --- | --- |
| `HelperTools` | MCP 도구 `helper_contacts`, `helper_ask`, `helper_answer` (`@Tool`). 실패하면 `{"error": {"code", "message"}}` |
| `ContactController` | `/api/v1/me/contacts` 목록·추가·삭제 (게이트웨이가 붙인 `X-User-Id`). 응답에 전화번호 없음 |
| `ReplyPageController` + `templates/reply.html` | 가족 답장 페이지 `/h/<token>`, 사진 `/h/<token>/photo` |
| `HelperService` | 검증(사진 JPEG·6MB, 질문 200자, 한 시간 10번), 보내기, 답 저장, 만료 |
| `ContactRepository`, `RequestRepository` | `helper` 스키마 SQL (JdbcClient). 상태 바꾸기는 `WHERE status = 'pending'`으로 한 번만 |
| `sms/` | `SMS_PROVIDER`: `nhncloud`, `solapi`, 비우면 로그만 |
| `photo/` | R2(AWS SDK S3), 값이 없으면 메모리 |
| `notify/` | 답 알림. 지금은 로그만(아래 "남은 것") |
| `db/migration/V1__init.sql` | 앱 레포 `docs/infra/schema.sql`의 helper 부분 그대로 |

## 설정

| 환경 변수 | 기본 | 설명 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | localhost:5432/fashion, fashion | 공용 DB |
| `HELPER_PUBLIC_BASE_URL` | http://localhost:8096 | 문자 링크의 도메인 |
| `SMS_PROVIDER` | (비움 = 로그만) | `nhncloud` 또는 `solapi` |
| `SMS_API_KEY`, `SMS_API_SECRET` | | NHN Cloud는 앱키·시크릿 키, 솔라피는 API 키·시크릿 |
| `SMS_SENDER` | | 사전 등록한 발신 번호 |
| `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET` | 버킷 `fashion-agent-helper` | 비우면 메모리(개발용) |

R2 버킷은 비공개로 두고, 대시보드 Settings → Object lifecycle rules에서 "1일 뒤 삭제" 규칙을 겁니다(서버가 멈춰도 사진이 남지 않게).

## 기술 선택 (2026-11-04 측정)

같은 기능을 세 가지로 만들어 PostgreSQL 16 + S3 호환 서버(S3Mock) 위에서 각 3번 돌렸습니다. 한 번에 helper_ask(300KB 사진) → 답장 페이지·사진 → 답 → helper_answer를 200번. 숫자는 3번의 범위입니다.

| | A. JdbcClient + AWS SDK (고름) | B. Spring Data JPA + AWS SDK | C. JdbcClient + MinIO 클라이언트 |
| --- | --- | --- | --- |
| 실행 jar | 47.8MB | 79.6MB | 59.7MB |
| 시작 시간 | 5.1–5.2초 | 8.7–9.1초 | 4.8–5.2초 |
| 메모리(시작 직후 → 200회 뒤) | 305–315 → 369–378MB | 380–404 → 514–529MB | 288–305 → 364–381MB |
| helper_ask p50 | 29–38ms | 34–36ms | 69–71ms |
| 답 저장 p50 | 17–20ms | 23–25ms | 17ms |

- DB 접근은 **JdbcClient**: 표가 2개뿐이고 SQL이 이미 `schema.sql`에 있어서, JPA(Hibernate)를 넣으면 jar +32MB, 시작 +3.5초, 메모리 +100MB 이상인데 얻는 게 적습니다. "답은 한 번만" 같은 조건부 UPDATE도 SQL로 바로 씁니다.
- R2 클라이언트는 **AWS SDK v2 (JDK HTTP 클라이언트)**: MinIO 클라이언트는 jar +12MB에 사진 올리기가 2배 느렸습니다. Cloudflare 문서도 AWS SDK를 예로 듭니다.
- 문자 업체는 둘 다 만들어 두고 `SMS_PROVIDER`로 바꿉니다. 요금 기준으로는 **NHN Cloud**를 먼저 씁니다(아래 표).

### 문자 업체 (2026-11-05 확인)

| | NHN Cloud | 솔라피 |
| --- | --- | --- |
| SMS(90바이트까지) | 9.9원 | 18원 (VAT 별도) |
| LMS | 30원 | 45원 (VAT 별도) |
| 가입 | 개인 가능, 신용카드 등록 필요 | 개인 가능 |
| 발신 번호 | 콘솔에서 휴대폰 본인 인증으로 등록 | 콘솔에서 등록 |

- 우리 문자는 링크(약 70자)와 질문이 들어가서 거의 항상 LMS입니다. 한 번 물을 때 NHN Cloud는 30원, 솔라피는 45원이고, 발표 준비로 수십 번 보내도 몇천 원 안쪽입니다.
- 발신 번호는 내 휴대폰 번호가 받는 사람에게 보이므로, 팀 공용 번호나 투넘버 서비스를 쓰는 걸 권합니다.
- 출처: [솔라피 가격 정책](https://solapi.com/pricing), [국내 메시지 발송 서비스 비교 (notifly, 2026-08)](https://blog.notifly.tech/messaging-vendor-comparison/), [NHN Cloud 가입·발신 번호 등록 후기 (2022)](https://velog.io/@yukina1418/%ED%95%B8%EB%93%9C%ED%8F%B0-%EB%B3%B8%EC%9D%B8%EC%9D%B8%EC%A6%9D-NHN-Cloud%EB%A1%9C-%EB%A7%8C%EB%93%A4%EC%96%B4%EB%B3%B4%EA%B8%B0). NHN Cloud 공식 요금 페이지는 이 환경에서 열리지 않아 비교 글의 숫자를 썼습니다. 가입할 때 콘솔에서 다시 확인하세요.

## 남은 것

- **답 알림(FCM)**: 푸시 토큰이 gateway의 `account.push_tokens`에 있어서 이 서버가 직접 못 읽습니다. gateway에 "사용자에게 푸시 보내기" 내부 API가 생기면 `notify/`만 바꾸면 됩니다. 그 전에는 사용자가 "답 왔어?" 하면 agent가 `helper_answer`로 확인합니다.
- 실제 문자 업체·R2 계정으로 한 번 보내 보기 (키는 서버 환경 변수로만).
