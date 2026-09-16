package com.sunmoon.appserver.infrastructure.order;

import com.sunmoon.appserver.domain.entry.BusinessDayStatus;
import com.sunmoon.appserver.domain.entry.BusinessDays;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * 매장 영업 상태를 OMS(Order)에 물어봅니다.
 *
 * <p>이것만 HTTP인 이유: 기준 정보가 아니라 <b>운영 상태</b>입니다. 메뉴나
 * 테이블은 사람이 가끔 고치는 것이라 fdw 뷰로 읽지만, 개점/마감은 POS에서
 * 계속 바뀌고 주문이 들어올 때마다 쓰이는 값이라 소유자에게 직접 물어보는
 * 편이 맞습니다 (umbrella {@code docs/adr/0006}).
 */
@Component
public class HttpBusinessDays implements BusinessDays {

    private static final ParameterizedTypeReference<Map<String, Object>> BODY =
            new ParameterizedTypeReference<>() {};

    private final RestClient orderClient;

    public HttpBusinessDays(RestClient orderClient) {
        this.orderClient = orderClient;
    }

    @Override
    public BusinessDayStatus statusOf(String storeId) {
        try {
            Map<String, Object> body = orderClient.get()
                    .uri(uri -> uri.path("/business-days/{storeId}").build(storeId))
                    .retrieve()
                    .body(BODY);
            if (body == null || !Boolean.TRUE.equals(body.get("open"))) {
                return BusinessDayStatus.closed();
            }
            return new BusinessDayStatus(true, String.valueOf(body.get("businessDate")));
        } catch (RestClientException e) {
            // 조용히 "닫힘"으로 바꾸지 않습니다 — 그러면 Order가 잠깐
            // 내려간 동안 모든 매장이 마감된 것처럼 보입니다.
            throw new StoreUnreachableException(storeId, e);
        }
    }
}
