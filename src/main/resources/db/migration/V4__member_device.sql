-- 회원 기기와 토큰 (umbrella docs/design/customer-app.md §3, A2 단계)
--
-- 회원번호는 **비밀이 아닙니다** — 열한 자리 숫자이고 손님이 적어 두었다가 칩니다. 번호만으로
-- 이력을 보여주면 남의 번호를 치면 남의 주문이 보입니다. 그래서 둘을 가릅니다:
--   · 주문에 번호 붙이기 → 회원번호 (그대로)
--   · 이력 보기          → 회원 토큰 — 그 번호를 받은 기기만 가짐
--
-- 새 기기로 옮기는 증명은 **영수증**입니다: 그 번호로 주문한 영수증의 QR 을 가진 사람이
-- 곧 그 번호의 사람입니다(9/24 영수증 QR 과 같은 규칙).

CREATE TABLE IF NOT EXISTS member_device (
    id          BIGSERIAL PRIMARY KEY,
    member_no   VARCHAR(16) NOT NULL REFERENCES member(member_no),
    token_hash  TEXT        NOT NULL UNIQUE,      -- SHA-256(회원 토큰). 토큰 자체는 저장 안 함
    platform    TEXT        NOT NULL,             -- WEB | ANDROID | IOS
    push_token  TEXT,                             -- FCM 등록 토큰 (앱만, A4)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen   TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_member_device_member ON member_device (member_no) WHERE revoked_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_member_device_push ON member_device (push_token) WHERE push_token IS NOT NULL;

-- 원장에서 주문 상태 화면(영수증 QR 화면)으로 가려면 그 주문의 영수증 토큰이 필요합니다.
-- OMS 가 주문을 만들 때 돌려준 값이라, OMS 가 돌려준 다른 값(주문번호·수령번호)과 한 곳에 둡니다.
ALTER TABLE app_oms_order_map ADD COLUMN IF NOT EXISTS claim_token TEXT;
