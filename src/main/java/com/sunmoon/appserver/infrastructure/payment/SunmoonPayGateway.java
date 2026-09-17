package com.sunmoon.appserver.infrastructure.payment;

import com.sunmoon.appserver.domain.order.AppOrder;
import com.sunmoon.appserver.domain.payment.PaymentGateway;
import com.sunmoon.appserver.domain.payment.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 가상의 자체 결제 — <b>무조건 승인합니다</b>.
 *
 * <p>진짜 PG가 붙기 전까지의 자리입니다. 중요한 것은 이 클래스가 하는
 * 일이 아니라 <b>흐름에 결제가 들어가 있다는 사실</b>입니다: 주문은
 * 이제 승인을 받고 나서 OMS로 갑니다. 그 순서가 없으면 PG를 붙이는 날
 * "결제 실패한 주문을 매장에 보내지 않으려면 어디를 고쳐야 하나"를
 * 처음부터 정해야 합니다.
 *
 * <p><b>승인번호를 진짜처럼 만듭니다.</b> 영수증과 정산이 거래를
 * 가리키는 값이라, 비워 두면 그 두 화면이 지금 만들어질 수 없습니다.
 * 모양은 {@code SMP-20260918-000001} — 날짜가 들어가서 하루치를 눈으로
 * 셀 수 있고, 진짜 PG의 승인번호와 헷갈리지 않게 접두사가 붙습니다.
 *
 * <p><b>현금과 같은 개념입니다</b> — 받았으면 돌려줍니다. 가상이라고
 * 환불을 생략하면, 매장이 못 받은 주문의 돈이 어디에도 없는 채로
 * 남습니다. 돌려준 것도 자기 번호를 달고 기록됩니다.
 *
 * <p>번호가 <b>메모리 카운터</b>인 것은 알고 두는 한계입니다. 재시작하면
 * 1부터 다시 시작하고, 서버가 둘이면 겹칩니다. 진짜 결제가 붙으면
 * 승인번호는 PG가 주므로 이 카운터는 그날 사라집니다 — 그때까지
 * 겹치더라도 잃는 것이 없는 값입니다(정산의 키가 아닙니다).
 */
@Component
public class SunmoonPayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SunmoonPayGateway.class);

    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AtomicInteger sequence = new AtomicInteger();

    @Override
    public Approval approve(AppOrder order) {
        // 금액이 0 이하인 주문은 승인하지 않습니다. 무조건 승인이라도
        // 이건 막습니다 — 0원 결제가 승인번호를 달고 정산에 들어가면
        // 그 줄이 무엇인지 아무도 설명할 수 없습니다.
        if (order.totalAmount() == null || order.totalAmount().signum() <= 0) {
            return Approval.refused(PaymentMethod.SUNMOON_PAY, "결제할 금액이 없습니다");
        }

        String approvalNo = "SMP-" + LocalDate.now(STORE_ZONE).format(DAY)
                + "-" + String.format("%06d", sequence.incrementAndGet());

        log.info("썬앤문페이 승인 {} — 주문 {} {}원",
                approvalNo, order.orderNo(), order.totalAmount());
        return Approval.approved(PaymentMethod.SUNMOON_PAY, approvalNo);
    }

    /**
     * 돌려줍니다. 가상이라 항상 됩니다.
     *
     * <p>환불번호에 {@code R} 을 붙여 승인번호와 가릅니다 — 같은 값을
     * 쓰면 정산에서 "받은 것"과 "돌려준 것"이 한 줄로 보이고, 그날
     * 금액이 안 맞는데 이유를 못 찾습니다.
     */
    @Override
    public Refund refund(AppOrder order, String approvalNo, String reason) {
        if (approvalNo == null || approvalNo.isBlank()) {
            // 무엇을 되돌리는지 모르면 환불이 아니라 그냥 돈을 주는
            // 것입니다. 가상이라도 그건 다른 일입니다.
            return Refund.failed("돌려줄 승인번호가 없습니다");
        }
        String refundNo = "SMPR-" + LocalDate.now(STORE_ZONE).format(DAY)
                + "-" + String.format("%06d", sequence.incrementAndGet());

        log.warn("썬앤문페이 환불 {} — 주문 {} {}원 ({}), 원승인 {}",
                refundNo, order.orderNo(), order.totalAmount(), reason, approvalNo);
        return Refund.done(refundNo);
    }
}
