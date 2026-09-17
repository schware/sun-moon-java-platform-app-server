package com.sunmoon.appserver.transport.http;

import com.sunmoon.appserver.domain.order.AppOrder;
import com.sunmoon.appserver.domain.order.AppOrderService;
import com.sunmoon.appserver.domain.order.OrderChannel;
import com.sunmoon.appserver.domain.order.PlaceAppOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * APP 주문 접수 — 손님 쪽 주문이 들어오는 자리 (업무 정의서 §4.4, §4.5).
 *
 * <p>여기가 <b>원장을 가진 쪽</b>입니다. 그래서 검증도 여기서 합니다:
 * 자리·매장·메뉴·결품·금액. 화면이 검증하고 서버가 적는 구조였다면
 * 화면을 하나 더 붙이는 날 검증이 두 벌이 되고 둘이 갈립니다.
 *
 * <p>거절의 상태 코드가 이유마다 다릅니다. 손님이 할 수 있는 일이
 * 다르기 때문입니다 — 마감이면 기다리면 되고(409), OMS에 못 닿은
 * 것이면 다시 눌러보면 되고(503), QR이 잘못됐으면 직원을 불러야
 * 합니다. 하나로 뭉치면 전부 직원을 부릅니다.
 */
@RestController
@RequestMapping("/orders")
@Tag(name = "AppOrders", description = "APP·QR 주문 접수와 조회")
public class AppOrderController {

    private final AppOrderService service;

    public AppOrderController(AppOrderService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "주문 접수",
            description = "원본을 로그에 남기고 검증한 뒤 번호를 채번해 원장에 적고, OMS로 넘깁니다. "
                    + "requestId 를 주면 같은 요청이 두 번 와도 주문은 하나입니다 — "
                    + "두 번째는 처음 만든 주문을 그대로 돌려줍니다.")
    @ApiResponse(responseCode = "201", description = "접수됨")
    @ApiResponse(responseCode = "400", description = "요청에 빠진 값이 있습니다")
    @ApiResponse(responseCode = "404", description = "그 매장의 오늘 메뉴판에 없는 메뉴")
    @ApiResponse(responseCode = "409", description = "자리·매장이 막혔거나, 결품이거나, OMS가 거절")
    @ApiResponse(responseCode = "503", description = "OMS에 닿지 못했습니다 — 다시 시도할 수 있습니다")
    public ResponseEntity<?> place(@RequestBody PlaceAppOrder request) {
        AppOrderService.Result result = service.place(request);
        if (result.accepted()) {
            return ResponseEntity.status(201).body(view(result.order()));
        }
        return ResponseEntity.status(result.refusal().status()).body(Map.of(
                "reason", result.refusal().name(),
                "message", result.message()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "주문 조회",
            description = "손님 화면의 주문 내역이 읽는 것은 OMS가 아니라 이 원장입니다. "
                    + "omsStatus 는 매장이 어디까지 했는지를 OMS에 물어본 값이고, "
                    + "원장의 status 와 **다른 축**이라 합치지 않고 나란히 둡니다. "
                    + "null 이면 아직 안 넘겼거나 OMS를 못 읽은 것입니다.")
    @ApiResponse(responseCode = "404", description = "No such order")
    public ResponseEntity<?> read(@PathVariable long id) {
        return service.track(id)
                .<ResponseEntity<?>>map(tracked -> {
                    Map<String, Object> body = view(tracked.order());
                    body.put("omsStatus", tracked.omsStatus());
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "존재하지 않는 주문입니다")));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "주문 취소",
            description = "취소 가능 여부는 **OMS가 판단합니다** — 매장이 이미 수락했으면 거절됩니다. "
                    + "APP이 상태를 짐작해서 미리 막지 않는 이유는, 수락과 취소가 겹치는 순간에 "
                    + "그 짐작이 틀리면 두 쪽이 서로 다른 사실을 믿게 되기 때문입니다.")
    @ApiResponse(responseCode = "200", description = "취소됨")
    @ApiResponse(responseCode = "409", description = "이미 준비 중이라 취소할 수 없습니다")
    @ApiResponse(responseCode = "503", description = "OMS에 닿지 못했습니다")
    public ResponseEntity<?> cancel(@PathVariable long id) {
        AppOrderService.Result result = service.cancel(id);
        if (result.accepted()) {
            return ResponseEntity.ok(view(result.order()));
        }
        return ResponseEntity.status(result.refusal().status()).body(Map.of(
                "reason", result.refusal().name(),
                "message", result.message()));
    }

    /**
     * 그 매장 그 판매일자의 주문들 — BO의 APP 매출 조회가 읽습니다.
     *
     * <p>{@code channel} 로 거릅니다. 전에는 BO가 "테이블 번호가 있는가"로
     * QR 주문을 짐작했는데, 그건 <b>도착지</b>이지 유입 경로가 아닙니다 —
     * 테이블 없는 QR이 생기는 날 그 짐작이 틀립니다.
     */
    @GetMapping
    @Operation(summary = "주문 목록",
            description = "판매일자 기준입니다. storeId 를 비우면 전 매장, "
                    + "channel 을 비우면 모든 경로입니다.")
    public java.util.List<Map<String, Object>> list(
            @RequestParam(required = false) String storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(required = false) OrderChannel channel) {
        return service.findByDay(storeId, businessDate, channel).stream()
                .map(AppOrderController::view)
                .toList();
    }

    /**
     * 손님 화면이 보는 모양.
     *
     * <p>record 를 그대로 내보내지 않는 이유: 원장에는 손님이 볼 이유가
     * 없는 값이 섞여 있고(내부 PK 같은 것), 화면이 그것을 읽기 시작하면
     * 나중에 바꿀 수 없습니다.
     */
    private static Map<String, Object> view(AppOrder order) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", order.id());
        body.put("orderNo", order.orderNo());
        body.put("storeId", order.storeId());
        body.put("businessDate", order.businessDate());
        body.put("channel", order.channel());
        body.put("type", order.type());
        body.put("status", order.status());
        body.put("floor", order.floor());
        body.put("tableNo", order.tableNo());
        body.put("totalAmount", order.totalAmount());
        body.put("lines", order.lines());
        // 수령번호는 OMS가 만든 값을 받아 적은 것입니다. 손님이 화면을
        // 닫으면 다시 못 보는 값이라 주문 조회에도 실어 보냅니다.
        body.put("pickupNo", order.pickupNo());
        body.put("pickupPin", order.pickupPin());
        body.put("placedAt", order.placedAt());

        // 옛 화면이 읽던 이름들. 화면을 한 번에 바꾸지 않아도 되게
        // 남겨 둡니다 — 주문 한 줄짜리라 첫 Line 이 곧 그 주문입니다.
        if (!order.lines().isEmpty()) {
            body.put("menuName", order.lines().get(0).menuName());
        }
        body.put("amount", order.totalAmount());
        return body;
    }
}
