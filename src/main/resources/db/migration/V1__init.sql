-- 앱 레포 docs/infra/schema.sql의 helper 부분 그대로 (바꿀 때는 그 문서를 먼저 고친다).
-- 스키마 helper는 Flyway(spring.flyway.schemas)와 앱 레포 init-schemas.sql이 만든다.

CREATE TABLE helper.contacts (
  id          varchar(40)  PRIMARY KEY,                   -- "h_" + 임의 문자열 (contactId)
  user_id     uuid         NOT NULL,
  name        varchar(50)  NOT NULL,                      -- 예: 엄마
  phone       varchar(20)  NOT NULL,                      -- 숫자만 저장(01000000000). API 응답·로그에 내보내지 않음
  relation    varchar(30),
  created_at  timestamptz  NOT NULL DEFAULT now(),
  updated_at  timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT contacts_user_phone_uq UNIQUE (user_id, phone)
);
CREATE INDEX contacts_user_idx ON helper.contacts (user_id);

CREATE TABLE helper.requests (
  id           varchar(40)  PRIMARY KEY,                  -- "r_" + 임의 문자열 (requestId)
  user_id      uuid         NOT NULL,
  contact_id   varchar(40)  NOT NULL REFERENCES helper.contacts(id) ON DELETE CASCADE,
  question     text         NOT NULL,
  image_key    varchar(200),                              -- R2 객체 키. 24시간 뒤 사진 지우면 null
  token        varchar(64)  NOT NULL UNIQUE,              -- /h/<token> 링크
  status       varchar(10)  NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'answered', 'expired')),
  answer       text,
  answered_at  timestamptz,
  expires_at   timestamptz  NOT NULL,
  created_at   timestamptz  NOT NULL DEFAULT now(),
  updated_at   timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT requests_answer_ck CHECK (status <> 'answered' OR (answer IS NOT NULL AND answered_at IS NOT NULL))
);
CREATE INDEX requests_user_idx ON helper.requests (user_id, created_at DESC);
-- 만료 처리 스케줄러: 아직 pending인 것만
CREATE INDEX requests_pending_expiry_idx ON helper.requests (expires_at) WHERE status = 'pending';
