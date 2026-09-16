package com.sunmoon.appserver.domain.entry;

/**
 * 매장이 지금 장사 중인지, 그렇다면 어느 판매일자인지.
 *
 * <p>이 사실은 BO가 아니라 <b>OMS(Order)</b>가 갖고 있습니다. 주문이
 * 들어올 때 판매일자를 찍는 쪽이 그쪽이고, 개점/마감은 POS에서 누릅니다
 * (umbrella {@code docs/adr/0006}). 그래서 이것만은 fdw 뷰가 아니라 HTTP로
 * 물어봅니다 — 기준 정보가 아니라 운영 상태이고, 계속 바뀝니다.
 */
public record BusinessDayStatus(boolean open, String businessDate) {

    public static BusinessDayStatus closed() {
        return new BusinessDayStatus(false, null);
    }
}
