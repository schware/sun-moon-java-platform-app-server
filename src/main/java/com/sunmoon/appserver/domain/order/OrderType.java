package com.sunmoon.appserver.domain.order;

/**
 * 주문이 <b>어떻게 나가나</b> (업무 정의서 §4.4.2).
 *
 * <p>OMS의 같은 이름 enum과 짝입니다 — 여기서 정해서 OMS로 넘깁니다.
 * 값이 둘뿐인 것은 지금 그것이 전부이기 때문입니다: APP 주문도 테이블
 * QR 주문도 손님이 매장에서 받아갑니다.
 */
public enum OrderType {

    /** 매장 주문. 손님이 매장에서 받아갑니다. */
    IN_STORE,
    /** 배달. 라이더가 가져가고 완료는 주문 채널이 알려줍니다. 아직 없습니다. */
    DELIVERY
}
