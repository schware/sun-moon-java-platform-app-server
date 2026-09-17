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
        return new PlaceAppOrder("store-01", "menu-01", null, requestId);
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

    @Test
    void aQrOrderCarriesItsSeatAndIsMarkedAsQrChannel() {
        AppOrderService.Result result = service.place(
                new PlaceAppOrder("store-99", "menu-01", "QR-CODE-1", "req-1"));

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

        @Override
        public void mapToOms(long appOrderId, String omsOrderNo, String pickupNo, String pickupPin) {
        }

        @Override
        public Optional<AppOrder> find(long id) {
            return saved.stream().filter(o -> o.id() == id).findFirst();
        }
    }

    private static final class FakeMenus implements MenuRepository {
        private boolean soldOut;

        @Override
        public List<Menu> findAll(String storeId, LocalDate on) {
            return List.of(new Menu("menu-01", "1메뉴", null, null, "kds-01",
                    new BigDecimal("6000"), 7, soldOut));
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

        @Override
        public Answer place(AppOrder order, String kdsDeviceId) {
            calls++;
            return answer;
        }
    }
}
