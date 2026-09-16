package com.sunmoon.appserver.infrastructure.order;

import com.sunmoon.appserver.domain.menu.OrderQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.OptionalInt;

/**
 * 주방 대기열을 OMS(Order)에 물어봅니다.
 *
 * <p>{@link com.sunmoon.appserver.infrastructure.order.HttpBusinessDays} 와
 * 달리 <b>실패를 삼킵니다</b>. 영업 상태는 못 읽으면 주문을 받을지 말지를
 * 정할 수 없어서 예외가 맞지만, 대기열은 예상시간을 꾸미는 값입니다 —
 * 그것 때문에 메뉴판이 안 나오면 손해가 더 큽니다.
 *
 * <p>마감한 매장은 OMS가 409로 답합니다. 그것도 "모르겠다"로 접습니다:
 * 마감 여부는 이미 게이트가 판단했고, 여기서 또 다룰 일이 아닙니다.
 */
@Component
public class HttpOrderQueue implements OrderQueue {

    private static final ParameterizedTypeReference<Map<String, Object>> BODY =
            new ParameterizedTypeReference<>() {};

    private static final Logger log = LoggerFactory.getLogger(HttpOrderQueue.class);

    private final RestClient orderClient;

    public HttpOrderQueue(RestClient orderClient) {
        this.orderClient = orderClient;
    }

    @Override
    public OptionalInt cookingAt(String storeId) {
        try {
            Map<String, Object> body = orderClient.get()
                    .uri(uri -> uri.path("/orders/queue").queryParam("storeId", storeId).build())
                    .retrieve()
                    .body(BODY);
            if (body == null || !(body.get("cooking") instanceof Number cooking)) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(cooking.intValue());
        } catch (Exception e) {
            log.debug("대기열을 읽지 못했습니다 ({}) — 예상시간 없이 내보냅니다", storeId, e);
            return OptionalInt.empty();
        }
    }
}
