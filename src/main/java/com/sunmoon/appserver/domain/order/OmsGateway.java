package com.sunmoon.appserver.domain.order;

/**
 * 주문을 OMS로 넘기는 창구.
 *
 * <p>인터페이스로 끊어둔 이유는 테스트 때문만이 아닙니다 — <b>거절과
 * 못 닿음을 반드시 갈라야</b> 하고, 그 구분을 답 타입이 강제합니다.
 * 앞은 손님에게 이유를 말할 수 있고, 뒤는 다시 보내야 하는 건입니다.
 */
public interface OmsGateway {

    /**
     * @param accepted   OMS가 접수했는가
     * @param reachable  OMS에 <b>닿기는 했는가</b>. false 면 거절이 아니라
     *                   전달 실패이고, 재처리 대상입니다.
     * @param omsOrderNo OMS가 붙인 주문번호. APP 주문번호와 다른 값입니다.
     * @param pickupNo   손님을 부르는 번호. OMS가 만듭니다.
     * @param pickupPin  손님이 증명하는 네 자리. OMS가 만듭니다.
     * @param message    손님에게 보여줄 문장. 거절 사유는 OMS가 압니다.
     */
    record Answer(boolean accepted, boolean reachable, String omsOrderNo,
                  String pickupNo, String pickupPin, String message, String claimToken) {

        public static Answer accepted(String omsOrderNo, String pickupNo, String pickupPin) {
            return accepted(omsOrderNo, pickupNo, pickupPin, null);
        }

        /** @param claimToken 영수증 QR 토큰 — 원장에서 주문 상태 화면으로 가는 열쇠 (customer-app §3.3) */
        public static Answer accepted(String omsOrderNo, String pickupNo, String pickupPin, String claimToken) {
            return new Answer(true, true, omsOrderNo, pickupNo, pickupPin, null, claimToken);
        }

        public static Answer rejected(String message) {
            return new Answer(false, true, null, null, null, message, null);
        }

        public static Answer unreachable(String message) {
            return new Answer(false, false, null, null, null, message, null);
        }
    }

    /**
     * 영수증 QR 토큰이 가리키는 주문의 손님 (OMS {@code GET /orders/claim/{token}/owner}, LAN 전용).
     * 회원 기기를 영수증으로 연결할 때만 씁니다. 모르면 비어 있음.
     */
    default java.util.Optional<String> claimOwner(String claimToken) {
        return java.util.Optional.empty();
    }

    /**
     * 승인된 결제 — OMS 에 주문과 함께 실어 POS 까지 갑니다 (umbrella store-app.md §6.2).
     * POS 가 거래 전문의 {@code payments} 에 이것을 적습니다. 결제가 POS 를 안 지나가서
     * 그동안 비어 있던 자리입니다.
     */
    record Paid(String method, String approvalNo, java.math.BigDecimal amount) {
    }

    /** @param paid 승인된 결제. null 이면 결제 없이 (옛 호출자). */
    Answer place(AppOrder order, String kdsDeviceId, Paid paid);

    default Answer place(AppOrder order, String kdsDeviceId) {
        return place(order, kdsDeviceId, null);
    }

    /**
     * OMS가 보는 지금 상태. APP 원장의 상태와 <b>다른 축</b>이라 합치지
     * 않고 나란히 보여줍니다 — 원장은 "손님의 요청이 어떻게 됐나"이고
     * 이쪽은 "매장이 어디까지 했나"입니다.
     *
     * <p>못 읽으면 {@code empty} 입니다. 그것 때문에 주문 조회가 통째로
     * 죽으면 안 됩니다 — 원장은 우리 것이라 항상 읽힙니다.
     */
    java.util.Optional<String> statusOf(String omsOrderNo);

    /**
     * 손님이 취소를 요청했습니다 (업무 정의서 §5.2).
     *
     * <p>취소 가능 여부는 <b>OMS가 판단합니다</b>. 매장이 이미 수락했으면
     * 거절하고, 그 답이 손님에게 갑니다 — APP이 상태를 짐작해서 미리
     * 막으면, 그 짐작이 틀린 순간(수락과 취소가 겹친 순간) 두 쪽이
     * 서로 다른 사실을 믿게 됩니다.
     */
    Answer cancel(String omsOrderNo);
}
