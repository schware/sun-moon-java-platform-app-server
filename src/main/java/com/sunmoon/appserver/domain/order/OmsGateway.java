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
                  String pickupNo, String pickupPin, String message) {

        public static Answer accepted(String omsOrderNo, String pickupNo, String pickupPin) {
            return new Answer(true, true, omsOrderNo, pickupNo, pickupPin, null);
        }

        public static Answer rejected(String message) {
            return new Answer(false, true, null, null, null, message);
        }

        public static Answer unreachable(String message) {
            return new Answer(false, false, null, null, null, message);
        }
    }

    Answer place(AppOrder order, String kdsDeviceId);
}
