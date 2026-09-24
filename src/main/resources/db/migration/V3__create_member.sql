-- 회원번호 (업무 정의서 §4.1, 2026-09-10에 정한 규칙)
--
-- 손님이 지금 익명입니다. 주문마다 `guest-xxxxxx` 를 하나 지어 붙이는데,
-- 그건 같은 손님이 두 번 시켜도 다른 사람으로 보인다는 뜻입니다.
--
-- **전화번호는 안 씁니다** — 개인정보이고, 받는 순간 보관과 파기의 의무가
-- 따라옵니다. **회원가입도 안 만듭니다** — 지금 필요한 것에 비해 너무
-- 큽니다. 그래서 Sun&Moon이 정한 모양이 이것입니다:
--
--       날짜 + 그날 들어온 순번 + 검증숫자
--
-- 그리고 그 규칙에는 대가가 하나 있습니다: **날마다 1번부터 다시 세므로
-- 카운터가 필요하고, 카운터는 어딘가에 적혀 있어야 합니다.**
--
-- 2026-09-10에는 그 자리가 "주문 채널에 자체 DB를 준다" 였습니다. 그때는
-- APP-SERVER가 없었습니다. 지금은 손님 주문의 원장을 APP-SERVER가 갖고
-- 있으므로(ADR-0012), 손님을 세는 카운터도 여기가 맞습니다 — 원장과 다른
-- 곳에 두면 "이 회원번호의 주문"을 물어볼 때마다 데이터베이스를 넘어가야
-- 합니다. 새 데이터베이스를 만들지 않아도 되는 것은 덤입니다.

-- 그날의 마지막 순번. app_order_seq 와 같은 방식입니다 — count(*)+1 로
-- 뽑으면 두 손님이 같은 순간에 받을 때 같은 번호가 나옵니다.
CREATE TABLE IF NOT EXISTS member_seq (
    business_date DATE PRIMARY KEY,
    last_seq INT NOT NULL DEFAULT 0
);

-- 발급된 회원번호.
--
-- 이 표가 있어야 "이 번호가 우리가 준 번호인가"에 답할 수 있습니다.
-- 검증숫자만으로는 모자랍니다 — 그건 **오타**를 잡는 값이지 지어낸
-- 번호를 잡는 값이 아닙니다. 11자리 중 10자리를 맞게 지으면 통과합니다.
--
-- 개인정보가 없습니다. 이름도 전화번호도 없고, 있는 것은 번호와 언제
-- 났는지뿐입니다. 그게 이 설계의 요점입니다.
CREATE TABLE IF NOT EXISTS member (
    member_no     VARCHAR(16) PRIMARY KEY,
    business_date DATE NOT NULL,
    seq           INT  NOT NULL,
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 같은 날 같은 순번이 두 번 나면 번호도 같아집니다. PK가 이미
    -- 막지만, 여기에도 걸어 두면 깨진 이유가 어느 쪽인지 드러납니다.
    UNIQUE (business_date, seq)
);

-- "이 손님의 주문" 을 물어보는 길. customer_id 에 회원번호가 들어갑니다.
CREATE INDEX IF NOT EXISTS idx_app_order_customer
    ON app_order_header (customer_id, business_date DESC);
