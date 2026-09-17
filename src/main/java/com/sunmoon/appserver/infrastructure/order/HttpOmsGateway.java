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
    public Answer place(AppOrder order, String kdsDeviceId) {
        AppOrder.Line line = order.lines().get(0);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("storeId", order.storeId());
        body.put("customerId", order.customerId());
        body.put("menuName", line.menuName());
        body.put("amount", order.totalAmount());
        body.put("type", order.type().name());
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
