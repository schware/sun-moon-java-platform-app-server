package com.sunmoon.appserver.domain.entry;

/**
 * 매장의 영업 상태를 물어보는 창구.
 *
 * <p>대답을 못 들은 것과 "닫혔다"는 대답은 다릅니다. 못 들었을 때 닫힌
 * 것으로 처리하면, Order를 재배포하는 30초 동안 모든 매장이 마감된 것처럼
 * 보입니다. 그래서 구현은 실패를 {@link StoreUnreachableException}으로
 * 던지고, 게이트가 그것을 따로 다룹니다.
 */
public interface BusinessDays {

    BusinessDayStatus statusOf(String storeId);

    /** 영업 상태를 물어볼 수 없었습니다 — 닫힌 것이 아닙니다. */
    class StoreUnreachableException extends RuntimeException {
        public StoreUnreachableException(String storeId, Throwable cause) {
            super("매장 영업 상태를 확인하지 못했습니다: " + storeId, cause);
        }
    }
}
