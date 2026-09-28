package com.sunmoon.appserver.infrastructure.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunmoon.appserver.domain.order.AppOrder;
import com.sunmoon.appserver.domain.order.OmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OMS(Order)에 주문을 넣습니다.
 *
 * <p>4xx/5xx에서 예외를 던지지 않게 해두었습니다 — 거절은 <b>답</b>이지
 * 실패가 아니고, 그 답의 문장이 손님 화면에 그대로 갑니다. 진짜 실패는
 * 연결이 안 되는 경우뿐이고, 그건 catch 로 갈립니다.
 */
@Component
public class HttpOmsGateway implements OmsGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpOmsGateway.class);

    private final RestClient orderClient;
    private final ObjectMapper objectMapper;

    public HttpOmsGateway(RestClient orderClient, ObjectMapper objectMapper) {
        this.orderClient = orderClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Answer place(AppOrder order, String kdsDeviceId, Paid paid) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("storeId", order.storeId());
        body.put("customerId", order.customerId());
        // 원장의 줄을 **그대로** 넘깁니다 (§4.4.2). 전에는 첫 줄의
        // 이름만 보내고 나머지를 버렸습니다 — 원장에는 세 줄이 있는데
        // 매장에는 한 줄만 간 상태였고, 그 둘이 어긋난 것을 아무도 볼
        // 수 없었습니다.
        body.put("lines", order.lines().stream().map(line -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("menuCode", line.menuCode());
            row.put("menuName", line.menuName());
            row.put("quantity", line.quantity());
            row.put("unitPrice", line.unitPrice());
            row.put("lineAmount", line.detailAmount());
            // 줄마다 주방. 이게 없으면 Order 가 주문 전체에 하나를
            // 붙이고, 첫 메뉴의 주방이 나머지까지 가져갑니다.
            row.put("kdsDeviceId", line.kdsDeviceId());
            return row;
        }).toList());
        // 옛 모양도 함께 보냅니다. Order 가 먼저 올라가고 이쪽이 나중에
        // 올라가는 순서를 강제하지 않기 위해서입니다 — 받는 쪽은 lines
        // 가 있으면 그것을 쓰고, 없으면 이 둘로 되돌아갑니다.
        body.put("menuName", com.sunmoon.appserver.domain.order.AppOrder.summaryOf(order.lines()));
        body.put("amount", order.totalAmount());
        body.put("type", order.type().name());
        if (paid != null) {
            Map<String, Object> payment = new LinkedHashMap<>();
            payment.put("method", paid.method());
            payment.put("approvalNo", paid.approvalNo());
            payment.put("amount", paid.amount());
            body.put("payment", payment);
        }
        if (kdsDeviceId != null) {
            body.put("kdsDeviceId", kdsDeviceId);
        }
        if (order.tableNo() != null) {
            body.put("floor", order.floor());
            body.put("tableNo", order.tableNo());
        }
        if (order.qrCode() != null) {
            body.put("qrCode", order.qrCode());
        }

        try {
            ResponseEntity<String> response = orderClient.post()
                    .uri("/orders")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .exchange((request, res) -> ResponseEntity
                            .status(res.getStatusCode())
                            .body(new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));

            String payload = response.getBody() == null ? "" : response.getBody();
            if (!response.getStatusCode().is2xxSuccessful()) {
                return Answer.rejected(messageOf(payload));
            }
            JsonNode node = objectMapper.readTree(payload);
            return Answer.accepted(
                    node.path("id").asText(null),
                    node.path("pickupNo").asText(null),
                    node.path("pickupPin").asText(null));
        } catch (Exception e) {
            // 닿지 못한 것. 거절과 갈라야 재처리 대상이 정해집니다.
            log.warn("OMS에 주문 {} 을 넘기지 못했습니다", order.orderNo(), e);
            return Answer.unreachable(null);
        }
    }

    @Override
    public java.util.Optional<String> statusOf(String omsOrderNo) {
        try {
            String payload = orderClient.get()
                    .uri(uri -> uri.path("/orders/{id}").build(omsOrderNo))
                    .retrieve()
                    .body(String.class);
            if (payload == null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.ofNullable(
                    objectMapper.readTree(payload).path("status").asText(null));
        } catch (Exception e) {
            // 못 읽어도 주문 조회는 살아야 합니다 — 원장은 우리 것입니다.
            log.debug("OMS 주문 {} 의 상태를 읽지 못했습니다", omsOrderNo, e);
            return java.util.Optional.empty();
        }
    }

    @Override
    public Answer cancel(String omsOrderNo) {
        try {
            ResponseEntity<String> response = orderClient.put()
                    .uri(uri -> uri.path("/orders/{id}/status").build(omsOrderNo))
                    .header("Content-Type", "application/json")
                    .body(Map.of("status", "CANCELLED"))
                    .exchange((request, res) -> ResponseEntity
                            .status(res.getStatusCode())
                            .body(new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));

            String payload = response.getBody() == null ? "" : response.getBody();
            return response.getStatusCode().is2xxSuccessful()
                    ? Answer.accepted(omsOrderNo, null, null)
                    : Answer.rejected(messageOf(payload));
        } catch (Exception e) {
            log.warn("OMS 주문 {} 을 취소하지 못했습니다", omsOrderNo, e);
            return Answer.unreachable(null);
        }
    }

    /** OMS의 오류 본문은 {@code {"error": "..."}} 입니다. 아니면 통째로 버립니다. */
    private String messageOf(String payload) {
        try {
            return objectMapper.readTree(payload).path("error").asText(null);
        } catch (Exception notJson) {
            return null;
        }
    }
}
