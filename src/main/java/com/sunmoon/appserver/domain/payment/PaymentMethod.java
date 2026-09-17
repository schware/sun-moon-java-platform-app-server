package com.sunmoon.appserver.domain.payment;

/**
 * 결제수단 (업무 정의서 §6.2 "카드사, 결제·할인 수단").
 *
 * <p>지금은 하나뿐입니다 — <b>sunmoon pay</b>. 가상의 수단이고 무조건
 * 승인됩니다. 실제 PG가 붙기 전까지 주문이 결제를 <b>지나가게</b> 하는
 * 것이 목적입니다: 결제 없이 주문이 나가는 흐름을 만들어 두면, PG를
 * 붙이는 날 "결제 전에 무엇을 해야 하나"를 처음부터 다시 정해야 합니다.
 *
 * <p>코드를 enum 으로 두되 <b>목록은 기준정보에서 다시 정의합니다</b>
 * (공통코드 {@code PAY_METHOD}). 여기 enum 은 "이 서버가 다룰 줄 아는
 * 방식"이고, 기준정보는 "이 브랜드가 받는 수단"입니다 — 둘은 다른
 * 목록이고, 카드사가 늘어난다고 코드를 배포하면 안 됩니다.
 */
public enum PaymentMethod {

    /** 가상의 자체 결제. 무조건 승인됩니다. */
    SUNMOON_PAY("썬앤문페이");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
