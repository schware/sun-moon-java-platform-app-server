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
    private AppOrderService service;

    @BeforeEach
    void setUp() {
        orders = new FakeOrders();
        menus = new FakeMenus();
        tables = new FakeTables();
        days = new FakeBusinessDays();
        oms = new FakeOms();
        service = new AppOrderService(
                orders,
                new MenuService(menus, new FakeQueue()),
                new TableEntryService(tables, days),
                days, oms, new ObjectMapper());
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
                null, "req-cart"));

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
                "store-01", null, List.of(new PlaceAppOrder.Item("menu-01", 3)), null, "req-1"));

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
                null, "req-1"));

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
                null, "req-1"));

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
                    new Menu("menu-02", "2메뉴", null, null, "kds-01",
                            new BigDecimal("9000"), 7, soldOut || secondSoldOut));
        }

        @Override
        public Optional<Menu> find(String storeId, String menuCode, LocalDate on) {
            return findAll(storeId, on).stream().filter(m -> m.menuCode().equals(menuCode)).findFirst();
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

    private static final class FakeOms implements OmsGateway {
        private Answer answer = Answer.accepted("777", "3-1", "1234");
        private int calls;

        private Answer cancelAnswer = Answer.accepted("777", null, null);
        private int cancelCalls;

        @Override
        public Answer place(AppOrder order, String kdsDeviceId) {
            calls++;
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
