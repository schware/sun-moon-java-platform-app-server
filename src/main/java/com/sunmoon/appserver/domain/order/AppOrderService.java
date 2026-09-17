package com.sunmoon.appserver.domain.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunmoon.appserver.domain.entry.BusinessDays;
import com.sunmoon.appserver.domain.entry.TableEntryService;
import com.sunmoon.appserver.domain.menu.MenuBoard;
import com.sunmoon.appserver.domain.menu.MenuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * APP 주문 접수 — 업무 정의서 §4.4.5 의 흐름 그대로입니다.
 *
 * <pre>
 *   원본 JSON 을 로그에 적고
 *   → 자리·매장·메뉴·금액을 검증하고
 *   → 업무 주문번호를 채번하고
 *   → Header·Detail 로 분해해 저장하고
 *   → OMS 로 보내고
 *   → 결과와 OMS 주문번호 매핑을 적고
 *   → 손님 화면에 답한다
 * </pre>
 *
 * <p><b>로그가 먼저인 것이 핵심입니다.</b> 검증에서 떨어지든 OMS가
 * 거절하든 손님이 무엇을 보냈는지는 남아야 합니다 — 그게 재처리의
 * 기준이고, "주문이 안 들어갔다"는 문의에 답할 수 있는 유일한 근거입니다.
 *
 * <p>이 일이 channel-order 가 아니라 여기 있는 이유: 원장을 가진 쪽이
 * 검증도 해야 합니다. 화면이 검증하고 서버가 적으면, 화면을 하나 더
 * 붙이는 날 검증이 두 벌이 되고 둘이 갈립니다.
 */
@Service
public class AppOrderService {

    private static final Logger log = LoggerFactory.getLogger(AppOrderService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppOrderRepository orders;
    private final MenuService menus;
    private final TableEntryService entries;
    private final BusinessDays businessDays;
    private final OmsGateway oms;
    private final ObjectMapper objectMapper;

    public AppOrderService(AppOrderRepository orders, MenuService menus, TableEntryService entries,
                           BusinessDays businessDays, OmsGateway oms, ObjectMapper objectMapper) {
        this.orders = orders;
        this.menus = menus;
        this.entries = entries;
        this.businessDays = businessDays;
        this.oms = oms;
        this.objectMapper = objectMapper;
    }

    /** 받았거나({@code order}), 못 받았거나({@code refusal}) — 하나만 채워집니다. */
    public record Result(AppOrder order, OrderRefusal refusal, String message) {

        static Result of(AppOrder order) {
            return new Result(order, null, null);
        }

        static Result refused(OrderRefusal refusal) {
            return new Result(null, refusal, refusal.message());
        }

        /** OMS가 준 문장을 그대로 손님에게 전합니다 — 이유는 OMS가 압니다. */
        static Result refused(OrderRefusal refusal, String upstreamMessage) {
            return new Result(null, refusal,
                    upstreamMessage == null || upstreamMessage.isBlank()
                            ? refusal.message() : upstreamMessage);
        }

        public boolean accepted() {
            return order != null;
        }
    }

    /** 손님 화면의 주문 내역이 읽는 것은 OMS가 아니라 이 원장입니다. */
    public Optional<AppOrder> find(long id) {
        return orders.find(id);
    }

    /** BO의 APP 매출 조회가 읽는 것 — 그 매장 그 판매일자의 주문들. */
    public List<AppOrder> findByDay(String storeId, LocalDate businessDate, OrderChannel channel) {
        return orders.findByDay(storeId, businessDate, channel);
    }

    /**
     * 원장의 상태와 OMS가 보는 상태를 <b>나란히</b> 돌려줍니다 (업무 정의서 §4.5).
     *
     * <p>합치지 않는 이유: 축이 다릅니다. 원장은 "손님의 요청이 어떻게
     * 됐나"이고 OMS는 "매장이 어디까지 했나"입니다. 한 칸으로 뭉개면
     * OMS에 넣지도 못한 주문(SEND_FAILED)을 표현할 수 없고, 그런
     * 주문이야말로 가장 잘 보여야 합니다.
     *
     * <p>{@code omsStatus} 가 null 이면 아직 안 넘겼거나 OMS를 못 읽은
     * 것입니다 — 둘 다 원장의 상태가 말해줍니다.
     */
    public record Tracked(AppOrder order, String omsStatus) {
    }

    public Optional<Tracked> track(long id) {
        return orders.find(id).map(order -> new Tracked(
                order,
                order.omsOrderNo() == null ? null : oms.statusOf(order.omsOrderNo()).orElse(null)));
    }

    /**
     * 손님이 주문을 무릅니다 (§5.2).
     *
     * <p><b>취소 가능 여부는 OMS가 판단합니다.</b> 여기서 상태를 보고
     * 미리 막지 않는 이유: 수락과 취소가 겹치는 순간이 있고, 그때 APP의
     * 짐작이 틀리면 두 쪽이 서로 다른 사실을 믿게 됩니다. 판단은 주문을
     * 소유한 쪽에서 한 번만 합니다.
     *
     * <p>OMS가 받아주면 원장도 따라 옮깁니다 — 원장이 손님 화면의
     * 근거라, 여기가 안 바뀌면 손님은 취소된 주문을 살아 있는 것으로
     * 계속 봅니다.
     */
    public Result cancel(long id) {
        AppOrder order = orders.find(id).orElse(null);
        if (order == null) {
            return Result.refused(OrderRefusal.INVALID_REQUEST);
        }
        if (order.omsOrderNo() == null) {
            // OMS에 넘어가지도 않은 주문입니다. 매장에 일감이 없으므로
            // 물어볼 곳도 없고, 원장에서 바로 닫습니다.
            orders.changeStatus(order.id(), AppOrderStatus.CANCELLED, "APP");
            return Result.of(orders.find(id).orElse(order));
        }

        OmsGateway.Answer answer = oms.cancel(order.omsOrderNo());
        if (!answer.accepted()) {
            return Result.refused(
                    answer.reachable() ? OrderRefusal.CANCEL_REFUSED : OrderRefusal.OMS_UNREACHABLE,
                    answer.message());
        }
        orders.changeStatus(order.id(), AppOrderStatus.CANCELLED, "OMS");
        return Result.of(orders.find(id).orElse(order));
    }

    public Result place(PlaceAppOrder request) {
        if (request.orderedItems().isEmpty()) {
            return Result.refused(OrderRefusal.INVALID_REQUEST);
        }

        String requestId = request.requestId() == null || request.requestId().isBlank()
                ? "gen-" + Long.toString(Math.abs(RANDOM.nextLong()), 36)
                : request.requestId();

        Optional<Long> logId = orders.logRequest(requestId, payloadOf(request));
        if (logId.isEmpty()) {
            // 같은 요청을 이미 처리했습니다. 새로 만들지 않고 그때 만든
            // 답을 돌려줍니다 — 그게 멱등성입니다. 아직 분해 전이면
            // 주문이 없을 수도 있는데, 그때는 잠시 후 다시 시도하는
            // 편이 두 번 주문하는 것보다 낫습니다.
            return orders.findByRequestId(requestId)
                    .map(Result::of)
                    .orElseGet(() -> Result.refused(OrderRefusal.OMS_UNREACHABLE));
        }
        return placeNew(logId.get(), requestId, request);
    }

    private Result placeNew(long logId, String requestId, PlaceAppOrder request) {
        // 1. 자리와 매장 — QR이면 게이트를 다시 지납니다. 손님이 메뉴를
        //    고르는 동안 매장이 마감했을 수 있고, 브라우저가 보낸 매장은
        //    믿지 않습니다.
        Placement placement = resolve(request);
        if (placement == null) {
            orders.completeLog(logId, null, "REFUSED", OrderRefusal.ENTRY_REFUSED.name());
            return Result.refused(OrderRefusal.ENTRY_REFUSED);
        }

        // 2. 메뉴 — 그 매장 그 날짜의 메뉴판에서 **줄마다** 찾습니다.
        //    결품과 가격 미등록이 여기서 걸립니다.
        //
        //    가격을 손님 화면에서 받지 않는 이유: 보낸 값을 믿으면 값을
        //    바꿔서 1원에 살 수 있습니다. 그리고 손님이 고르는 동안
        //    가격이 바뀌었을 수도 있는데, 맞는 값은 접수되는 순간의
        //    값입니다.
        MenuBoard board = menus.board(placement.storeId(), placement.businessDate());
        List<AppOrder.Line> lines = new java.util.ArrayList<>();
        MenuBoard.Item first = null;
        int detailSeq = 1;
        for (PlaceAppOrder.Item ordered : request.orderedItems()) {
            MenuBoard.Item item = board.menus().stream()
                    .filter(m -> m.menuCode().equals(ordered.menuId()))
                    .findFirst().orElse(null);
            if (item == null) {
                orders.completeLog(logId, null, "REFUSED", OrderRefusal.UNKNOWN_MENU.name());
                return Result.refused(OrderRefusal.UNKNOWN_MENU,
                        "메뉴를 찾을 수 없습니다: " + ordered.menuId());
            }
            if (!item.orderable()) {
                // **어느 메뉴가 안 되는지 말해 줍니다.** 장바구니에 다섯
                // 줄이 있는데 "주문할 수 없습니다"만 돌려주면 손님은
                // 하나씩 빼 보는 수밖에 없습니다.
                orders.completeLog(logId, null, "REFUSED", OrderRefusal.NOT_ORDERABLE.name());
                return Result.refused(OrderRefusal.NOT_ORDERABLE,
                        item.name() + " 은(는) 지금 주문할 수 없습니다");
            }
            if (first == null) {
                first = item;
            }
            BigDecimal unit = item.price();
            lines.add(new AppOrder.Line(detailSeq++, item.menuCode(), item.name(),
                    ordered.quantity(), unit,
                    unit.multiply(BigDecimal.valueOf(ordered.quantity()))));
        }

        // 3. 채번하고 분해해 저장합니다. 여기까지가 "손님의 요청이
        //    원장에 남았다"이고, OMS가 어떻게 답하든 이 사실은 남습니다.
        int seq = orders.nextSeq(placement.storeId(), placement.businessDate());
        BigDecimal price = lines.stream()
                .map(AppOrder.Line::detailAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        MenuBoard.Item item = first;
        AppOrder saved = orders.save(new AppOrder(
                null,
                AppOrderNo.of(placement.storeId(), placement.businessDate(), seq),
                placement.storeId(),
                placement.businessDate(),
                request.channel(),
                // 지금 들어오는 주문은 전부 매장 주문입니다. 배달이
                // 붙는 날 요청에서 받아 옵니다.
                OrderType.IN_STORE,
                "guest-" + Long.toString(Math.abs(RANDOM.nextLong()), 36).substring(0, 6),
                placement.floor(),
                placement.tableNo(),
                placement.qrCode(),
                price,
                AppOrderStatus.CREATED,
                lines,
                null, null, null, null, null));

        // 4. OMS로 넘깁니다. 여기부터 매장의 일입니다.
        //
        //    주방은 **첫 줄의 것을 씁니다**. 한 주문의 메뉴들이 서로 다른
        //    주방으로 가야 하는 경우(중식과 일식을 같이 파는 매장)는
        //    아직 못 다룹니다 — 주문 하나에 주방이 하나라는 전제가
        //    Order 의 kdsDeviceId 에도 그대로 있습니다. 갈라야 하는 날
        //    주문을 쪼갤지 줄마다 주방을 달지부터 정해야 합니다.
        OmsGateway.Answer answer = oms.place(saved, item == null ? null : item.kdsDeviceId());
        if (answer.accepted()) {
            orders.mapToOms(saved.id(), answer.omsOrderNo(), answer.pickupNo(), answer.pickupPin());
            orders.changeStatus(saved.id(), AppOrderStatus.SENT, "OMS");
            orders.completeLog(logId, saved.id(), "SENT", null);
            return Result.of(orders.find(saved.id()).orElse(saved));
        }

        AppOrderStatus failure = answer.reachable() ? AppOrderStatus.REJECTED : AppOrderStatus.SEND_FAILED;
        orders.changeStatus(saved.id(), failure, "OMS");
        orders.completeLog(logId, saved.id(), failure.name(), answer.message());
        log.info("APP 주문 {} 이 OMS에서 {} — {}", saved.orderNo(), failure, answer.message());

        return Result.refused(
                answer.reachable() ? OrderRefusal.OMS_REJECTED : OrderRefusal.OMS_UNREACHABLE,
                answer.message());
    }

    /** 주문이 갈 자리. QR이면 게이트가, 아니면 매장의 영업 상태가 정합니다. */
    private record Placement(String storeId, LocalDate businessDate,
                             Integer floor, String tableNo, String qrCode) {
    }

    private Placement resolve(PlaceAppOrder request) {
        if (request.fromQr()) {
            TableEntryService.Result gate = entries.enter(request.qrCode());
            if (!gate.allowed()) {
                return null;
            }
            var entry = gate.entry();
            return new Placement(entry.storeId(), LocalDate.parse(entry.businessDate()),
                    entry.floor(), entry.tableNo(), request.qrCode());
        }

        if (request.storeId() == null || request.storeId().isBlank()) {
            return null;
        }
        try {
            var status = businessDays.statusOf(request.storeId());
            if (!status.open()) {
                return null;
            }
            return new Placement(request.storeId(), LocalDate.parse(status.businessDate()),
                    null, null, null);
        } catch (BusinessDays.StoreUnreachableException e) {
            return null;
        }
    }

    private String payloadOf(PlaceAppOrder request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            // 원본을 못 적으면 그 자체가 사고이지만, 여기서 멈추면
            // 손님이 주문을 못 합니다. 최소한 무엇이었는지는 남깁니다.
            return "{\"unserializable\":true}";
        }
    }
}
