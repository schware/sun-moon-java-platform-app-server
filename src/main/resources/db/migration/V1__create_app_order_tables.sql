-- APP 주문 원장 (업무 정의서 §4.4)
--
-- APP-SERVER가 처음으로 **자기 데이터를 소유**합니다. 지금까지 이
-- 데이터베이스에 있던 것은 BO를 가리키는 postgres_fdw 외부 테이블과
-- 그 위의 뷰뿐이었습니다 — 읽기만 하는 것들입니다.
--
-- 왜 OMS가 이미 주문을 들고 있는데 또 두는가: 두 주문이 같은 것이
-- 아니기 때문입니다. OMS의 주문은 **매장이 처리하는 일감**이고, 여기
-- 주문은 **손님이 요청한 것**입니다. 손님이 요청했지만 OMS가 거절한
-- 것도 APP에는 남아야 하고(§4.4.6 재처리), 손님 화면의 주문내역은
-- OMS 상태가 아니라 이 원장을 읽습니다.

-- 1. 원본 로그 ------------------------------------------------------
--
-- 분해하기 **전에** 먼저 씁니다. 검증에서 떨어지든 OMS가 거절하든,
-- 손님이 무엇을 보냈는지는 남아야 합니다 — 그게 재처리의 기준이고,
-- "안 들어갔다"는 문의에 답할 수 있는 유일한 근거입니다.
--
-- request_id 가 멱등성 키입니다 (§4.4.6). 손님이 버튼을 두 번 누르거나
-- 네트워크 재시도가 겹쳐도 주문이 둘이 되지 않게 하는 것은 유일 제약
-- 하나입니다 — 애플리케이션에서 "이미 있나"를 먼저 보는 방식은 그
-- 확인과 쓰기 사이에 창이 남습니다.
CREATE TABLE IF NOT EXISTS app_order_log (
    id BIGSERIAL PRIMARY KEY,
    request_id TEXT NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL,
    error TEXT,
    app_order_id BIGINT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (request_id)
);

-- 2. Header ---------------------------------------------------------
--
-- id 는 내부 PK, order_no 는 사람이 부르는 업무 주문번호입니다. 둘을
-- 가르는 것이 문서가 권한 방향입니다(§4.4.1) — 업무번호에 점포가
-- 들어가면 운영자가 읽기 쉽지만, 점포 코드가 바뀌거나 길이가 문제될 때
-- PK까지 흔들리면 안 됩니다.
--
-- 브랜드는 아직 없습니다. BO에 브랜드 마스터가 생기면 order_no 앞에
-- 붙고, 그때까지는 점포 + 판매일자 + 일련번호입니다. 지어낸 자리를
-- 미리 비워두지 않는 이유는, 빈 자리가 들어간 번호가 이미 인쇄되고
-- 영수증에 찍히기 때문입니다.
--
-- order_channel 은 **어디로 들어왔나**(APP / QR)이고 order_type 은
-- **어떻게 나가나**(매장 / 배달)입니다. 서로 다른 질문이라 칸도
-- 둘입니다 — QR로 들어온 배달도, APP으로 들어온 매장 식사도 있을 수
-- 있습니다.
CREATE TABLE IF NOT EXISTS app_order_header (
    id BIGSERIAL PRIMARY KEY,
    order_no TEXT NOT NULL,
    store_id TEXT NOT NULL,
    business_date DATE NOT NULL,
    order_channel TEXT NOT NULL,
    order_type TEXT NOT NULL,
    customer_id TEXT,
    floor INT,
    table_no TEXT,
    qr_code TEXT,
    total_amount NUMERIC(12,2) NOT NULL,
    status TEXT NOT NULL,
    placed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_no)
);

CREATE INDEX IF NOT EXISTS idx_app_order_header_day
    ON app_order_header (store_id, business_date);

-- 3. Detail ---------------------------------------------------------
--
-- 지금은 한 주문에 한 줄뿐입니다 — 장바구니가 없어서 메뉴를 하나씩
-- 주문합니다. 그래도 Header 에 메뉴를 박지 않고 표를 가르는 이유는,
-- 장바구니가 붙는 날 Header 를 뜯지 않아도 되기 때문입니다.
CREATE TABLE IF NOT EXISTS app_order_detail (
    id BIGSERIAL PRIMARY KEY,
    app_order_id BIGINT NOT NULL REFERENCES app_order_header(id) ON DELETE CASCADE,
    detail_seq INT NOT NULL,
    menu_code TEXT NOT NULL,
    menu_name TEXT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price NUMERIC(12,2) NOT NULL,
    detail_amount NUMERIC(12,2) NOT NULL,
    UNIQUE (app_order_id, detail_seq)
);

-- 4. Payment --------------------------------------------------------
--
-- **아직 한 줄도 안 들어옵니다.** 결제가 없기 때문입니다. 그래도 표를
-- 두는 이유는 빈 표를 만드는 값이 싸기 때문입니다 — 나중에 결제사가
-- 정해지면 컬럼을 고치면 되고, 빈 표를 고치는 것은 공짜입니다.
-- 반대로 표가 없으면 그날 Header 에 결제 칸을 급히 붙이게 됩니다.
--
-- 컬럼은 잠정입니다. 승인 참조값의 모양은 결제사가 정합니다.
CREATE TABLE IF NOT EXISTS app_order_payment (
    id BIGSERIAL PRIMARY KEY,
    app_order_id BIGINT NOT NULL REFERENCES app_order_header(id) ON DELETE CASCADE,
    payment_seq INT NOT NULL,
    payment_type TEXT NOT NULL,
    payment_amount NUMERIC(12,2) NOT NULL,
    payment_status TEXT NOT NULL,
    approval_ref TEXT,
    paid_at TIMESTAMPTZ,
    UNIQUE (app_order_id, payment_seq)
);

-- 5. 상태 이력 -------------------------------------------------------
--
-- Header 의 status 는 지금 어디인가이고, 여기는 어떻게 왔는가입니다.
-- 둘 다 필요한 이유: "언제부터 이 상태였나"를 Header 만으로는 답할 수
-- 없고, 지연을 재려면 각 걸음의 시각이 있어야 합니다.
CREATE TABLE IF NOT EXISTS app_order_status_hist (
    id BIGSERIAL PRIMARY KEY,
    app_order_id BIGINT NOT NULL REFERENCES app_order_header(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    source TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_app_order_status_hist_order
    ON app_order_status_hist (app_order_id, changed_at);

-- 6. OMS 주문번호 매핑 ------------------------------------------------
--
-- APP 주문번호와 OMS 주문번호는 **다른 번호**입니다 (§4.4.1, 사전조사
-- 문서 §8.1). 같은 번호로 쓰면 둘 중 하나가 바뀌는 날 모든 연결이
-- 끊깁니다. 외부 채널 주문번호가 생기면 칸이 하나 더 붙습니다.
-- pickup_no / pickup_pin 도 여기입니다. **OMS가 만든 값**이고 APP은
-- 받아 적을 뿐이라, Header(우리 것)가 아니라 이 표(OMS와의 연결)에
-- 둡니다. 손님 화면이 주문내역에서 수령번호를 보여주려면 필요한데,
-- 손님 브라우저가 OMS를 직접 부를 수는 없습니다 — LAN 전용이고,
-- 애초에 그쪽 경계가 아닙니다.
CREATE TABLE IF NOT EXISTS app_oms_order_map (
    app_order_id BIGINT PRIMARY KEY REFERENCES app_order_header(id) ON DELETE CASCADE,
    oms_order_no TEXT NOT NULL,
    pickup_no TEXT,
    pickup_pin TEXT,
    mapped_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (oms_order_no)
);

-- 7. 업무 주문번호 채번 -----------------------------------------------
--
-- count(*)+1 로 뽑지 않는 이유는 Order 의 수령번호와 같습니다: 두 손님이
-- 같은 순간에 주문하면 같은 번호를 받습니다. UPSERT 한 문장이 증가와
-- 읽기를 함께 합니다.
--
-- 판매일자로 갈라 두면 매일 1번부터 다시 시작합니다.
CREATE TABLE IF NOT EXISTS app_order_seq (
    store_id TEXT NOT NULL,
    business_date DATE NOT NULL,
    last_seq INT NOT NULL DEFAULT 0,
    PRIMARY KEY (store_id, business_date)
);
