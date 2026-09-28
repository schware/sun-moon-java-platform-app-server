package com.sunmoon.appserver.domain.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunmoon.appserver.domain.entry.BusinessDayStatus;
import com.sunmoon.appserver.domain.entry.BusinessDays;
import com.sunmoon.appserver.domain.entry.StoreTable;
import com.sunmoon.appserver.domain.entry.TableDirectory;
import com.sunmoon.appserver.domain.entry.TableEntryService;
import com.sunmoon.appserver.domain.menu.Menu;
import com.sunmoon.appserver.domain.menu.MenuRepository;
import com.sunmoon.appserver.domain.menu.MenuService;
import com.sunmoon.appserver.domain.menu.OrderQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 접수의 규칙 — 업무 정의서 §4.4.5, §4.4.6.
 *
 * <p>제일 중요한 것은 <b>거절과 못 닿음을 가르는 것</b>과 <b>같은 요청이
 * 두 번 와도 주문이 하나인 것</b>입니다. 앞은 손님에게 할 말이 달라지고,
 * 뒤는 안 되면 손님이 두 번 결제하게 됩니다.
 */
class AppOrderServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 17);
    private static final Instant NOW = Instant.parse("2026-09-17T01:00:00Z");

    private FakeOrders orders;
    private FakeMenus menus;
    private FakeTables tables;
    private FakeBusinessDays days;
    private FakeOms oms;
    private FakePay pay;
    /** 결제수단 목록의 답. 기본은 켜져 있음 — 나머지 시험들은 결제수단 얘기가 아닙니다. */
    private com.sunmoon.appserver.domain.payment.AcceptedMethods.Acceptance acceptance;
    private AppOrderService service;

    @BeforeEach
    void setUp() {
        orders = new FakeOrders();
        menus = new FakeMenus();
        tables = new FakeTables();
        days = new FakeBusinessDays();
        oms = new FakeOms();
        pay = new FakePay();
        acceptance = com.sunmoon.appserver.domain.payment.AcceptedMethods.Acceptance.ACCEPTED;
        service = new AppOrderService(
                orders,
                new MenuService(menus, new FakeQueue()),
                new TableEntryService(tables, days),
                days, oms, pay,
                method -> acceptance,
                // 회원번호를 안 대는 주문이 이 시험들의 전부입니다 —
                // 익명이 계속 정상 경로입니다.
                new com.sunmoon.appserver.domain.member.MemberService(new FakeMembers()),
                new ObjectMapper());
    }

    // ---- 결제수단 (BO 기준정보, 2026-09-27) ----------------------------

    @Test
    void 결제수단이_꺼져_있으면_원장에_쓰기_전에_막는다() {
        acceptance = com.sunmoon.appserver.domain.payment.AcceptedMethods.Acceptance.DISABLED;

        AppOrderService.Result result = service.place(request("req-off"));

        assertThat(result.refusal()).isEqualTo(OrderRefusal.PAYMENT_METHOD_DISABLED);
        // 원장에 아무것도 없어야 합니다 — 받지 않을 주문을 적어 두면
        // 승인도 환불도 없는 PAYMENT_FAILED 줄만 쌓입니다.
        assertThat(orders.saved).isEmpty();
        // 결제를 시도하지도, 매장에 보내지도 않았어야 합니다.
        assertThat(pay.approvedOrders).isEmpty();
        assertThat(oms.calls).isZero();
    }

    @Test
    void 결제수단_목록을_못_읽으면_받는다() {
        // 모르는 것과 꺼진 것은 다릅니다. 모르는 채로 막으면 기준정보를
        // 읽는 길 하나가 장사 전체를 멈춥니다.
        acceptance = com.sunmoon.appserver.domain.payment.AcceptedMethods.Acceptance.UNKNOWN;

        AppOrderService.Result result = service.place(request("req-unknown"));

        assertThat(result.accepted()).isTrue();
    }

    private static PlaceAppOrder request(String requestId) {
        return PlaceAppOrder.single("store-01", "menu-01", null, requestId);
    }

    @Test
    void placesAnOrderAndWritesItToTheLedger() {
        AppOrderService.Result result = service.place(request("req-1"));

        assertThat(result.accepted()).isTrue();
        AppOrder order = result.order();
        assertThat(order.orderNo()).isEqualTo("store-01-20260917-0001");
        assertThat(order.channel()).isEqualTo(OrderChannel.APP);
        assertThat(order.type()).isEqualTo(OrderType.IN_STORE);
        assertThat(order.lines()).hasSize(1);
        assertThat(order.totalAmount()).isEqualByComparingTo("6000");
    }

    /**
     * 같은 요청이 두 번 오면 주문은 하나입니다 (§4.4.6).
     *
     * <p>이게 안 되면 손님이 버튼을 두 번 누르거나 네트워크가 재시도할
     * 때마다 주문이 늘어납니다 — 매장에 두 그릇이 만들어집니다.
     */
    @Test
    void theSameRequestTwiceIsOneOrder() {
        AppOrderService.Result first = service.place(request("req-1"));
        AppOrderService.Result second = service.place(request("req-1"));

        assertThat(first.accepted()).isTrue();
        assertThat(second.accepted()).isTrue();
        assertThat(second.order().id()).isEqualTo(first.order().id());
        assertThat(orders.saved).hasSize(1);
        // OMS에도 한 번만 갑니다 — 두 번 가면 매장에 일감이 둘입니다.
        assertThat(oms.calls).isEqualTo(1);
    }

    /**
     * 승인된 결제가 주문과 함께 매장으로 갑니다 (store-app.md §6.2) — POS 가 거래 전문의
     * 결제로 적는 값입니다. 그동안 결제가 POS 를 안 지나가서 매출의 결제가 비어 있었습니다.
     */
    @Test
    void theApprovedPaymentTravelsWithTheOrder() {
        AppOrderService.Result result = service.place(request("req-paid"));

        assertThat(result.accepted()).isTrue();
        assertThat(oms.lastPaid).isNotNull();
        assertThat(oms.lastPaid.approvalNo()).isNotBlank();
        assertThat(oms.lastPaid.amount()).isEqualByComparingTo(result.order().totalAmount());
    }

    @Test
    void differentRequestIdsAreDifferentOrders() {
        service.place(request("req-1"));
        service.place(request("req-2"));

        assertThat(orders.saved).hasSize(2);
        assertThat(orders.saved.get(1).orderNo()).isEqualTo("store-01-20260917-0002");
    }

    /**
     * OMS가 거절해도 원장에는 남습니다.
     *
     * <p>손님에게는 "요청했는데 거절됐다"가 일어난 일입니다. 안 남기면
     * "주문이 안 들어갔다"는 문의에 답할 근거가 없습니다.
     */
    @Test
    void aRejectedOrderStaysInTheLedger() {
        oms.answer = OmsGateway.Answer.rejected("마감한 매장입니다");

        AppOrderService.Result result = service.place(request("req-1"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.refusal()).isEqualTo(OrderRefusal.OMS_REJECTED);
        // OMS가 준 문장을 그대로 전합니다 — 이유는 OMS가 압니다.
        assertThat(result.message()).isEqualTo("마감한 매장입니다");
        assertThat(orders.saved).hasSize(1);
        assertThat(orders.statuses).containsExactly(AppOrderStatus.REJECTED);
    }

    /**
     * 못 닿은 것은 거절이 아닙니다.
     *
     * <p>거절은 답을 받은 것이고 이건 못 받은 것이라, 재처리 대상이
     * 다릅니다 — 상태도 HTTP 코드도 갈라야 합니다.
     */
    @Test
    void anUnreachableOmsIsNotARejection() {
        oms.answer = OmsGateway.Answer.unreachable(null);

        AppOrderService.Result result = service.place(request("req-1"));

        assertThat(result.refusal()).isEqualTo(OrderRefusal.OMS_UNREACHABLE);
        assertThat(result.refusal().status()).isEqualTo(503);
        assertThat(orders.statuses).containsExactly(AppOrderStatus.SEND_FAILED);
    }

    @Test
    void aSoldOutMenuNeverReachesOms() {
        menus.soldOut = true;

        AppOrderService.Result result = service.place(request("req-1"));

        assertThat(result.refusal()).isEqualTo(OrderRefusal.NOT_ORDERABLE);
        assertThat(orders.saved).isEmpty();
        assertThat(oms.calls).isZero();
    }

    @Test
    void aClosedStoreIsRefusedBeforeNumbering() {
        days.open = false;

        AppOrderService.Result result = service.place(request("req-1"));

        assertThat(result.refusal()).isEqualTo(OrderRefusal.ENTRY_REFUSED);
        // 번호를 태우지 않습니다 — 거절된 주문에 번호가 붙으면 그날
        // 번호가 구멍 난 채로 이어집니다.
        assertThat(orders.seqCalls).isZero();
    }

    /**
     * <b>결제는 OMS로 가기 전입니다</b> (§4.10).
     *
     * <p>순서가 요점입니다. 매장이 만들기 시작한 뒤에 결제가 실패하면
     * 음식은 이미 나가 있습니다. 지금은 무조건 승인이라 차이가 없지만,
     * 진짜 PG가 붙는 날 이 순서가 이미 맞아 있어야 합니다.
     */
    @Test
    void paymentHappensBeforeTheStoreIsTold() {
        service.place(request("req-1"));

        assertThat(pay.approvedOrders).hasSize(1);
        assertThat(orders.payments).hasSize(1);
        assertThat(orders.payments.get(0).status()).isEqualTo("APPROVED");
        // 승인번호가 비어 있으면 영수증도 정산도 그 거래를 못 가리킵니다.
        assertThat(orders.payments.get(0).approvalRef()).isNotBlank();
    }

    /** 결제가 거절되면 OMS로 <b>가지 않습니다</b> — 돈이 안 된 주문을 매장이 만들면 안 됩니다. */
    @Test
    void aRefusedPaymentNeverReachesTheStore() {
        pay.approve = false;

        AppOrderService.Result result = service.place(request("req-1"));

        assertThat(result.refusal()).isEqualTo(OrderRefusal.PAYMENT_REFUSED);
        assertThat(result.refusal().status()).isEqualTo(402);
        assertThat(oms.calls).isZero();
        // 원장에는 남습니다 — 손님에게는 그것도 일어난 일입니다.
        assertThat(orders.saved).hasSize(1);
        assertThat(orders.statuses).contains(AppOrderStatus.PAYMENT_FAILED);
    }

    /**
     * <b>돈은 받았는데 주문이 매장에 못 갔으면 돌려줍니다.</b>
     *
     * <p>썬앤문페이는 가상이지만 현금과 같은 개념입니다 — 받았으면
     * 돌려줄 수 있어야 합니다. 안 돌려주면 손님은 돈을 냈고 음식은 없는
     * 상태로 남고, 그걸 알아채는 길은 손님의 전화뿐입니다.
     */
    @Test
    void moneyTakenButTheOrderNeverArrivedIsGivenBack() {
        oms.answer = OmsGateway.Answer.rejected("마감한 매장입니다");

        service.place(request("req-1"));

        assertThat(pay.refunded).containsExactly("SMP-TEST-000001");
        assertThat(orders.refunds).hasSize(1);
    }

    /** 손님이 취소해도 돌려줍니다 (§5.2). */
    @Test
    void cancellingGivesTheMoneyBack() {
        AppOrder placed = service.place(request("req-1")).order();

        service.cancel(placed.id());

        assertThat(pay.refunded).hasSize(1);
        assertThat(orders.refunds).hasSize(1);
    }

    /**
     * 취소를 두 번 눌러도 <b>한 번만</b> 돌려줍니다.
     *
     * <p>저장소가 아직 안 돌려준 것만 찾아 주므로 두 번째는 돌려줄
     * 것이 없습니다. 가상 결제라도 두 번 돌려주면 장부가 안 맞습니다.
     */
    @Test
    void cancellingTwiceRefundsOnce() {
        AppOrder placed = service.place(request("req-1")).order();

        service.cancel(placed.id());
        service.cancel(placed.id());

        assertThat(pay.refunded).hasSize(1);
    }

    /**
     * 환불에 실패하면 결제는 <b>승인 상태로 남습니다</b>.
     *
     * <p>그게 맞습니다: 돈이 아직 우리 쪽에 있다는 뜻이고, 갚을 것이
     * 있다는 사실이 장부에 남아야 합니다. "환불했다"고 적고 실패하면
     * 갚을 것이 조용히 사라집니다.
     */
    @Test
    void aFailedRefundLeavesTheDebtOnTheBooks() {
        pay.refundWorks = false;
        oms.answer = OmsGateway.Answer.rejected("마감한 매장입니다");

        service.place(request("req-1"));

        assertThat(orders.refunds).isEmpty();
        assertThat(orders.payments.get(0).status()).isEqualTo("APPROVED");
    }

    /**
     * <b>주방이 줄마다 실려 갑니다</b> (2026-09-18).
     *
     * <p>그전에는 주문 전체에 첫 줄의 주방을 붙였습니다. 그래서 첫
     * 메뉴가 kds-02 것이면 짜장면까지 kds-02 로 갔고, kds-01 은 자기
     * 것을 못 봤습니다.
     */
    @Test
    void eachLineCarriesItsOwnKitchen() {
        AppOrderService.Result result = service.place(new PlaceAppOrder(
                "store-01", null,
                List.of(new PlaceAppOrder.Item("menu-01", 1),
                        new PlaceAppOrder.Item("menu-02", 1)),
                null, null, "req-1"));

        assertThat(result.order().lines())
                .extracting(AppOrder.Line::kdsDeviceId)
                .containsExactly("kds-01", "kds-02");
    }

    /**
     * 장바구니 — 여러 줄이 한 주문으로 갑니다 (§4.4.2).
     *
     * <p>이게 없던 동안 손님이 두 가지를 시키면 주문이 둘로 갈렸고,
     * 수령번호도 둘이었습니다.
     */
    @Test
    void aCartBecomesOneOrderWithSeveralLines() {
        AppOrderService.Result result = service.place(new PlaceAppOrder(
                "store-01", null,
                List.of(new PlaceAppOrder.Item("menu-01", 2),
                        new PlaceAppOrder.Item("menu-02", 1)),
                null, null, "req-cart"));

        assertThat(result.accepted()).isTrue();
        AppOrder order = result.order();
        assertThat(order.lines()).hasSize(2);
        assertThat(order.lines()).extracting(AppOrder.Line::detailSeq).containsExactly(1, 2);
        // 6000 x 2 + 9000
        assertThat(order.totalAmount()).isEqualByComparingTo("21000");
    }

    /**
     * <b>가격은 서버가 정합니다.</b>
     *
     * <p>요청에 가격 칸이 아예 없습니다 — 손님 화면이 보낸 값을 믿으면
     * 값을 바꿔서 1원에 살 수 있고, 손님이 고르는 동안 가격이 바뀌었을
     * 수도 있습니다. 맞는 값은 접수되는 순간의 메뉴판 값입니다.
     */
    @Test
    void thePriceComesFromTheMenuBoardNotTheRequest() {
        AppOrderService.Result result = service.place(new PlaceAppOrder(
                "store-01", null, List.of(new PlaceAppOrder.Item("menu-01", 3)), null, null, "req-1"));

        assertThat(result.order().lines().get(0).unitPrice()).isEqualByComparingTo("6000");
        assertThat(result.order().totalAmount()).isEqualByComparingTo("18000");
    }

    /**
     * 결품이 하나면 <b>그 메뉴 이름을 말해 줍니다</b>.
     *
     * <p>장바구니에 다섯 줄이 있는데 "주문할 수 없습니다"만 돌려주면
     * 손님은 하나씩 빼 보는 수밖에 없습니다.
     */
    @Test
    void aSoldOutLineNamesItself() {
        menus.secondSoldOut = true;

        AppOrderService.Result result = service.place(new PlaceAppOrder(
                "store-01", null,
                List.of(new PlaceAppOrder.Item("menu-01", 1),
                        new PlaceAppOrder.Item("menu-02", 1)),
                null, null, "req-1"));

        assertThat(result.refusal()).isEqualTo(OrderRefusal.NOT_ORDERABLE);
        assertThat(result.message()).contains("2메뉴");
        // 한 줄이라도 막히면 주문 전체가 안 들어갑니다 — 반쪽짜리
        // 장바구니를 매장에 보내면 손님은 시키지도 않은 주문을 받습니다.
        assertThat(orders.saved).isEmpty();
    }

    /** 같은 메뉴를 두 줄로 보내면 <b>합치지 않습니다</b> — 줄마다 옵션이 붙는 날 되돌릴 수 없습니다. */
    @Test
    void theSameMenuTwiceStaysTwoLines() {
        AppOrderService.Result result = service.place(new PlaceAppOrder(
                "store-01", null,
                List.of(new PlaceAppOrder.Item("menu-01", 1),
                        new PlaceAppOrder.Item("menu-01", 1)),
                null, null, "req-1"));

        assertThat(result.order().lines()).hasSize(2);
    }

    /** 수량을 안 보내거나 0으로 보내면 1입니다 — 아무것도 안 시킨 줄이 남으면 안 됩니다. */
    @Test
    void aMissingQuantityIsOne() {
        assertThat(new PlaceAppOrder.Item("menu-01", 0).quantity()).isEqualTo(1);
    }

    /** 옛 모양(menuId 하나)도 그대로 받습니다 — 화면과 서버가 한날한시에 안 바뀝니다. */
    @Test
    void theOldSingleMenuShapeStillWorks() {
        AppOrderService.Result result = service.place(request("req-old"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.order().lines()).hasSize(1);
    }

    /**
     * 취소 가능 여부는 <b>OMS가 판단합니다</b>.
     *
     * <p>APP이 상태를 보고 미리 막지 않는 이유: 수락과 취소가 겹치는
     * 순간이 있고, 그때 APP의 짐작이 틀리면 두 쪽이 서로 다른 사실을
     * 믿게 됩니다.
     */
    @Test
    void cancellingAsksOmsAndFollowsItsAnswer() {
        AppOrder placed = service.place(request("req-1")).order();

        AppOrderService.Result result = service.cancel(placed.id());

        assertThat(result.accepted()).isTrue();
        assertThat(oms.cancelCalls).isEqualTo(1);
        assertThat(orders.statuses).contains(AppOrderStatus.CANCELLED);
    }

    @Test
    void anOrderTheStoreAlreadyTookCannotBeCancelled() {
        AppOrder placed = service.place(request("req-1")).order();
        oms.cancelAnswer = OmsGateway.Answer.rejected("이미 수락된 주문입니다");

        AppOrderService.Result result = service.cancel(placed.id());

        assertThat(result.refusal()).isEqualTo(OrderRefusal.CANCEL_REFUSED);
        assertThat(result.message()).isEqualTo("이미 수락된 주문입니다");
    }

    /**
     * OMS에 넘어가지도 않은 주문은 물어볼 곳이 없습니다 — 매장에 일감이
     * 없으므로 원장에서 바로 닫습니다.
     */
    @Test
    void anOrderThatNeverReachedOmsIsClosedLocally() {
        oms.answer = OmsGateway.Answer.unreachable(null);
        service.place(request("req-1"));
        long id = orders.saved.get(0).id();
        oms.cancelCalls = 0;

        AppOrderService.Result result = service.cancel(id);

        assertThat(result.accepted()).isTrue();
        assertThat(oms.cancelCalls).isZero();
    }

    @Test
    void aQrOrderCarriesItsSeatAndIsMarkedAsQrChannel() {
        AppOrderService.Result result = service.place(
                PlaceAppOrder.single("store-99", "menu-01", "QR-CODE-1", "req-1"));

        assertThat(result.accepted()).isTrue();
        AppOrder order = result.order();
        assertThat(order.channel()).isEqualTo(OrderChannel.QR);
        // 브라우저가 보낸 store-99 는 무시되고 QR이 가리키는 매장이 됩니다.
        assertThat(order.storeId()).isEqualTo("store-01");
        assertThat(order.tableNo()).isEqualTo("3");
        assertThat(order.qrCode()).isEqualTo("QR-CODE-1");
    }

    // --- fakes -------------------------------------------------------

    private static final class FakeOrders implements AppOrderRepository {
        private final AtomicLong ids = new AtomicLong();
        private final List<String> requests = new ArrayList<>();
        private final List<AppOrder> saved = new ArrayList<>();
        private final List<AppOrderStatus> statuses = new ArrayList<>();
        /** 결제 한 줄. 실제 저장소의 app_order_payment 를 흉내 냅니다. */
        private final List<Payment> payments = new ArrayList<>();
        private final List<Long> refunds = new ArrayList<>();

        record Payment(long orderId, String method, java.math.BigDecimal amount,
                       String status, String approvalRef) {
        }
        private int seq;
        private int seqCalls;

        @Override
        public Optional<Long> logRequest(String requestId, String payloadJson) {
            if (requests.contains(requestId)) {
                return Optional.empty();
            }
            requests.add(requestId);
            return Optional.of((long) requests.size());
        }

        @Override
        public Optional<AppOrder> findByRequestId(String requestId) {
            int index = requests.indexOf(requestId);
            return index < 0 || index >= saved.size() ? Optional.empty() : Optional.of(saved.get(index));
        }

        @Override
        public int nextSeq(String storeId, LocalDate businessDate) {
            seqCalls++;
            return ++seq;
        }

        @Override
        public AppOrder save(AppOrder order) {
            AppOrder withId = new AppOrder(ids.incrementAndGet(), order.orderNo(), order.storeId(),
                    order.businessDate(), order.channel(), order.type(), order.customerId(),
                    order.floor(), order.tableNo(), order.qrCode(), order.totalAmount(),
                    order.status(), order.lines(), null, null, null, NOW, NOW);
            saved.add(withId);
            return withId;
        }

        @Override
        public void changeStatus(long appOrderId, AppOrderStatus status, String source) {
            statuses.add(status);
        }

        @Override
        public void completeLog(long logId, Long appOrderId, String status, String error) {
        }

        /**
         * 진짜 저장소는 매핑을 적고, 그 다음 find() 가 그 값을 실어
         * 돌려줍니다. fake 가 이걸 버리면 "OMS 주문번호가 없는 주문"이
         * 되어 취소가 원장 안에서만 닫히고, 테스트가 실제와 다른 길을
         * 걷습니다 — 실제로 그렇게 한 번 어긋났습니다.
         */
        @Override
        public void mapToOms(long appOrderId, String omsOrderNo, String pickupNo, String pickupPin) {
            for (int i = 0; i < saved.size(); i++) {
                AppOrder o = saved.get(i);
                if (o.id() == appOrderId) {
                    saved.set(i, new AppOrder(o.id(), o.orderNo(), o.storeId(), o.businessDate(),
                            o.channel(), o.type(), o.customerId(), o.floor(), o.tableNo(), o.qrCode(),
                            o.totalAmount(), o.status(), o.lines(), omsOrderNo, pickupNo, pickupPin,
                            o.placedAt(), o.updatedAt()));
                }
            }
        }

        @Override
        public Optional<AppOrder> find(long id) {
            return saved.stream().filter(o -> o.id() == id).findFirst();
        }

        @Override
        public void savePayment(long appOrderId, String method, java.math.BigDecimal amount,
                                String status, String approvalRef) {
            payments.add(new Payment(appOrderId, method, amount, status, approvalRef));
        }

        /**
         * 아직 안 돌려준 승인번호. 이 조건이 <b>두 번 환불을 막습니다</b> —
         * 실제 저장소의 {@code payment_status = 'APPROVED'} 와 같은 규칙입니다.
         */
        @Override
        public java.util.Optional<String> unrefundedApprovalOf(long appOrderId) {
            return payments.stream()
                    .filter(p -> p.orderId() == appOrderId && "APPROVED".equals(p.status()))
                    .map(Payment::approvalRef)
                    .findFirst();
        }

        @Override
        public void markRefunded(long appOrderId, String refundNo) {
            refunds.add(appOrderId);
            payments.replaceAll(p -> p.orderId() == appOrderId && "APPROVED".equals(p.status())
                    ? new Payment(p.orderId(), p.method(), p.amount(), "REFUNDED",
                            p.approvalRef() + " / " + refundNo)
                    : p);
        }

        @Override
        public List<AppOrder> findByDay(String storeId, LocalDate businessDate, OrderChannel channel) {
            return saved.stream()
                    .filter(o -> storeId == null || o.storeId().equals(storeId))
                    .filter(o -> channel == null || o.channel() == channel)
                    .toList();
        }
    }

    private static final class FakeMenus implements MenuRepository {
        private boolean soldOut;
        /** menu-02 만 결품인 상태 — 장바구니에서 어느 줄이 막혔는지 가릴 때 씁니다. */
        private boolean secondSoldOut;

        @Override
        public List<Menu> findAll(String storeId, LocalDate on) {
            return List.of(
                    new Menu("menu-01", "1메뉴", null, null, "kds-01",
                            new BigDecimal("6000"), 7, soldOut),
                    // 일부러 다른 주방입니다 — 한 주문이 두 주방에
                    // 걸치는 경우를 시험에서 다룹니다.
                    new Menu("menu-02", "2메뉴", null, null, "kds-02",
                            new BigDecimal("9000"), 7, soldOut || secondSoldOut));
        }

        @Override
        public Optional<Menu> find(String storeId, String menuCode, LocalDate on) {
            return findAll(storeId, on).stream().filter(m -> m.menuCode().equals(menuCode)).findFirst();
        }
    }

    /**
     * 회원번호 저장소 — 아무것도 발급된 적이 없는 상태입니다.
     *
     * <p>그래서 어떤 번호를 대도 "모르는 번호"가 되고, 주문은 익명으로
     * 들어갑니다. 이 시험들이 확인하는 것이 그 경로라 그게 맞습니다.
     */
    private static final class FakeMembers
            implements com.sunmoon.appserver.domain.member.MemberRepository {
        private int seq;

        @Override
        public int nextSeq(java.time.LocalDate businessDate) {
            return ++seq;
        }

        @Override
        public void save(com.sunmoon.appserver.domain.member.Member member) {
        }

        @Override
        public java.util.Optional<com.sunmoon.appserver.domain.member.Member> find(String memberNo) {
            return java.util.Optional.empty();
        }
    }

    private static final class FakeQueue implements OrderQueue {
        @Override
        public OptionalInt cookingAt(String storeId) {
            return OptionalInt.of(0);
        }
    }

    private static final class FakeTables implements TableDirectory {
        @Override
        public Optional<StoreTable> byQrCode(String qrCode) {
            return "QR-CODE-1".equals(qrCode)
                    ? Optional.of(new StoreTable(1L, "store-01", 1, "3", null, true))
                    : Optional.empty();
        }

        @Override
        public Optional<String> storeName(String storeId) {
            return "store-01".equals(storeId) ? Optional.of("1매장") : Optional.empty();
        }
    }

    private static final class FakeBusinessDays implements BusinessDays {
        private boolean open = true;

        @Override
        public BusinessDayStatus statusOf(String storeId) {
            return open ? new BusinessDayStatus(true, DAY.toString()) : BusinessDayStatus.closed();
        }
    }

    /** 무조건 승인하는 썬앤문페이 — 거절도 시험할 수 있게 스위치를 둡니다. */
    private static final class FakePay
            implements com.sunmoon.appserver.domain.payment.PaymentGateway {
        private boolean approve = true;
        private boolean refundWorks = true;
        private final List<AppOrder> approvedOrders = new ArrayList<>();
        private final List<String> refunded = new ArrayList<>();

        @Override
        public Approval approve(AppOrder order) {
            if (!approve) {
                return Approval.refused(
                        com.sunmoon.appserver.domain.payment.PaymentMethod.SUNMOON_PAY,
                        "잔액이 부족합니다");
            }
            approvedOrders.add(order);
            return Approval.approved(
                    com.sunmoon.appserver.domain.payment.PaymentMethod.SUNMOON_PAY,
                    "SMP-TEST-000001");
        }

        @Override
        public Refund refund(AppOrder order, String approvalNo, String reason) {
            if (!refundWorks) {
                return Refund.failed("돌려주지 못했습니다");
            }
            refunded.add(approvalNo);
            return Refund.done("SMPR-TEST-000001");
        }
    }

    private static final class FakeOms implements OmsGateway {
        private Answer answer = Answer.accepted("777", "3-1", "1234");
        private int calls;

        private Answer cancelAnswer = Answer.accepted("777", null, null);
        private int cancelCalls;

        private Paid lastPaid;

        @Override
        public Answer place(AppOrder order, String kdsDeviceId, Paid paid) {
            calls++;
            lastPaid = paid;
            return answer;
        }

        @Override
        public Optional<String> statusOf(String omsOrderNo) {
            return Optional.of("PLACED");
        }

        @Override
        public Answer cancel(String omsOrderNo) {
            cancelCalls++;
            return cancelAnswer;
        }
    }
}
