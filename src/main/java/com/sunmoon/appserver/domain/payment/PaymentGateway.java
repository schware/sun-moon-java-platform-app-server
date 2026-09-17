package com.sunmoon.appserver.domain.payment;

import com.sunmoon.appserver.domain.order.AppOrder;

/**
 * 결제를 승인받는 자리 (업무 정의서 §4.10 결제 연동).
 *
 * <p>지금 구현은 무조건 승인하는 {@code SunmoonPayGateway} 하나입니다.
 * 그런데 <b>인터페이스를 먼저 두는 것</b>이 요점입니다: 승인이 거절될
 * 수 있다는 사실이 흐름에 들어가 있어야, 진짜 PG를 붙이는 날 주문
 * 흐름을 다시 짜지 않습니다.
 *
 * <p>그래서 거절 경로가 지금도 <b>동작합니다</b> — 쓰이지 않을 뿐입니다.
 * 쓰이지 않는 길을 만들어 두는 것이 보통은 낭비지만, 결제는 "나중에
 * 끼워 넣기"가 제일 비싼 자리입니다.
 */
public interface PaymentGateway {

    /**
     * 이 주문의 금액을 승인받습니다.
     *
     * <p>주문 전체가 한 건입니다. 여러 수단으로 나눠 내는 것(현금 +
     * 카드)은 POS 매출에는 이미 있지만(TR Payment 가 여러 줄) 손님
     * 채널에는 아직 없습니다 — 필요해지는 날 이 자리가 목록을 돌려주게
     * 바뀝니다.
     */
    Approval approve(AppOrder order);

    /**
     * 받은 돈을 <b>돌려줍니다</b>.
     *
     * <p>썬앤문페이는 가상이지만 <b>현금과 같은 개념</b>입니다 — 받았으면
     * 돌려줄 수 있어야 하고, 돌려준 것도 기록이 남아야 합니다. 받기만
     * 하고 돌려주는 길이 없으면, 매장이 못 받은 주문의 돈이 어디에도
     * 없는 채로 남습니다.
     *
     * <p>돌려줄 일은 둘입니다: OMS가 주문을 거절했거나(매장에 못 갔음),
     * 손님이 취소했거나(§5.2).
     *
     * @param approvalNo 돌려줄 승인번호. 무엇을 되돌리는지 가리키는 값이라,
     *                   없으면 환불이 아니라 그냥 돈을 주는 것이 됩니다.
     */
    Refund refund(AppOrder order, String approvalNo, String reason);

    /**
     * @param approvalNo 승인번호. 거절이면 null 입니다. 영수증과 정산이
     *                   거래를 가리키는 값이라, 승인된 건에는 <b>반드시</b>
     *                   있어야 합니다.
     * @param message    거절 사유. 손님에게 그대로 보여줄 문장입니다.
     */
    record Approval(boolean approved, PaymentMethod method, String approvalNo, String message) {

        public static Approval approved(PaymentMethod method, String approvalNo) {
            return new Approval(true, method, approvalNo, null);
        }

        public static Approval refused(PaymentMethod method, String message) {
            return new Approval(false, method, null, message);
        }
    }

    /**
     * @param refundNo 환불번호. 승인번호와 <b>다른 값</b>입니다 — 같은 값을
     *                 쓰면 정산에서 "받은 것"과 "돌려준 것"이 한 줄로
     *                 보이고, 그날 금액이 안 맞습니다.
     */
    record Refund(boolean refunded, String refundNo, String message) {

        public static Refund done(String refundNo) {
            return new Refund(true, refundNo, null);
        }

        /**
         * 못 돌려줬습니다.
         *
         * <p>지금 구현에서는 안 나오지만 자리는 둡니다 — 진짜 결제가
         * 붙으면 환불도 실패합니다(카드가 해지됐거나, 기간이 지났거나).
         * 그때 "환불했다"고 적어 두면 갚을 것이 사라집니다.
         */
        public static Refund failed(String message) {
            return new Refund(false, null, message);
        }
    }
}
