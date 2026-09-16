package com.sunmoon.appserver.domain.entry;

/**
 * QR을 찍고 메뉴 화면으로 넘어가기 전에 확인된 것 — 어느 매장의 어느
 * 자리이고, 지금 주문을 받을 수 있는가.
 *
 * <p>게이트를 메뉴 <b>앞</b>에 두는 이유가 있습니다. 지금 주문 채널은
 * 메뉴를 다 고르고 주문을 넣는 순간에야 마감된 매장이라는 걸 알려줍니다 —
 * 손님이 헛수고를 한 뒤에요. 진입 시점으로 옮기면 그게 사라집니다.
 *
 * <p>{@code businessDate}는 매장이 지금 올라타 있는 판매일자입니다. 주문이
 * 그 날짜로 기록되므로, 손님 화면에도 같은 값이 보여야 나중에 영수증과
 * 대조가 됩니다.
 */
public record TableEntry(
        String storeId,
        String storeName,
        int floor,
        String tableNo,
        String tableLabel,
        String businessDate) {
}
