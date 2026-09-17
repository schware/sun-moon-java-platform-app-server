package com.sunmoon.appserver.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 손님이 요청한 주문 한 건 — APP-SERVER가 소유하는 원장 (업무 정의서 §4.4).
 *
 * <p><b>OMS의 주문과 같은 것이 아닙니다.</b> 저쪽은 매장이 처리하는
 * 일감이고, 이쪽은 손님이 요청한 것입니다. 그래서 OMS가 거절한 주문도
 * 여기에는 남습니다 — 손님에게는 "요청했는데 거절됐다"가 일어난 일이고,
 * 매장에는 일감이 생기지 않은 것뿐입니다.
 *
 * <p>Header / Detail 로 가른 것은 지금 필요해서가 아닙니다 — 장바구니가
 * 없어서 Detail 은 항상 한 줄입니다. 가르는 이유는 장바구니가 붙는 날
 * Header 를 뜯지 않아도 되기 때문입니다.
 *
 * <p>{@code channel} 과 {@code type} 은 다른 질문의 답입니다:
 * <b>어디로 들어왔나</b>(APP / QR)와 <b>어떻게 나가나</b>(매장 / 배달).
 * QR로 들어온 배달도, APP으로 들어온 매장 식사도 있을 수 있습니다.
 */
public record AppOrder(
        Long id,
        String orderNo,
        String storeId,
        LocalDate businessDate,
        OrderChannel channel,
        OrderType type,
        String customerId,
        Integer floor,
        String tableNo,
        String qrCode,
        BigDecimal totalAmount,
        AppOrderStatus status,
        List<Line> lines,
        /**
         * OMS가 돌려준 값들 — 주문번호와 수령번호. <b>우리가 만든 것이
         * 아니라 받아 적은 것</b>이고, OMS로 넘기기 전에는 전부 null
         * 입니다.
         *
         * <p>손님 화면의 주문내역이 수령번호를 보여주려면 필요합니다.
         * 손님 브라우저가 OMS를 직접 부를 수는 없습니다 — LAN 전용이고,
         * 애초에 그쪽 경계가 아닙니다.
         */
        String omsOrderNo,
        String pickupNo,
        String pickupPin,
        Instant placedAt,
        Instant updatedAt) {

    /**
     * 주문 한 줄. {@code detailSeq} 는 1부터이고, 같은 주문 안에서만
     * 뜻이 있습니다 — 전역 번호가 아니라 "이 주문의 몇 번째"입니다.
     */
    public record Line(
            int detailSeq,
            String menuCode,
            String menuName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal detailAmount,
            /**
             * 이 줄을 만드는 주방.
             *
             * <p>메뉴 마스터가 정합니다. <b>줄마다</b> 들고 가는 이유:
             * 주문 하나가 두 주방에 걸칠 수 있습니다 — 중식과 일식을
             * 같이 파는 매장에서 짜장면과 초밥을 함께 시킨 경우.
             *
             * <p>2026-09-18 이전에는 주문 전체에 <b>첫 줄의 주방</b>을
             * 붙여 보냈고, 그래서 첫 메뉴가 kds-02 것이면 짜장면까지
             * kds-02 로 갔습니다.
             */
            String kdsDeviceId) {

        public static Line single(String menuCode, String menuName, BigDecimal unitPrice) {
            return new Line(1, menuCode, menuName, 1, unitPrice, unitPrice, null);
        }
    }

    /**
     * 화면과 매장에 쓸 한 줄 요약 — "짜장면 외 2건".
     *
     * <p>Order 의 {@code menuName} 과 같은 규칙입니다. 두 곳이 서로 다른
     * 문장을 만들면 손님 화면과 POS 화면에 다른 이름이 뜹니다.
     */
    public static String summaryOf(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        String first = lines.get(0).menuName();
        return lines.size() == 1 ? first : first + " 외 " + (lines.size() - 1) + "건";
    }

    /** 테이블 QR로 들어온 주문인가 — 가져다 줄 자리가 있는가. */
    public boolean toTable() {
        return tableNo != null;
    }
}
