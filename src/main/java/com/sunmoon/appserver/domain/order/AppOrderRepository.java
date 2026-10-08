package com.sunmoon.appserver.domain.order;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * APP 주문 원장. 이 서비스가 <b>쓰는</b> 첫 저장소입니다 — 나머지(메뉴,
 * 테이블)는 BO를 읽기만 하는 뷰였습니다.
 */
public interface AppOrderRepository {

    /**
     * 원본 요청을 분해하기 <b>전에</b> 적습니다 (업무 정의서 §4.4.5).
     *
     * <p>검증에서 떨어지든 OMS가 거절하든 손님이 무엇을 보냈는지는
     * 남아야 합니다 — 재처리의 기준이고, "주문이 안 들어갔다"는 문의에
     * 답할 수 있는 유일한 근거입니다.
     *
     * <p>{@code requestId} 가 같은 요청이 이미 있으면 {@code empty} 입니다.
     * 유일 제약이 막은 것이고, 그게 멱등성입니다 (§4.4.6) — 애플리케이션이
     * "이미 있나"를 먼저 보는 방식은 그 확인과 쓰기 사이에 창이 남습니다.
     */
    Optional<Long> logRequest(String requestId, String payloadJson);

    /** 이미 처리한 요청의 결과. 두 번째 요청에 같은 답을 돌려주기 위한 것입니다. */
    Optional<AppOrder> findByRequestId(String requestId);

    /**
     * 그 매장 그 판매일자의 다음 일련번호. 세는 것이 아니라 <b>올리고
     * 돌려받는</b> 것입니다 — count(*)+1 이면 두 손님이 같은 순간에
     * 주문할 때 같은 번호를 받습니다.
     */
    int nextSeq(String storeId, LocalDate businessDate);

    /** Header + Detail 을 한 트랜잭션으로. 반쪽만 남은 주문은 원장이 아닙니다. */
    AppOrder save(AppOrder order);

    /** 상태를 옮기고 이력을 한 줄 남깁니다. */
    void changeStatus(long appOrderId, AppOrderStatus status, String source);

    /** 로그 행을 주문과 잇고 처리 결과를 적습니다. */
    void completeLog(long logId, Long appOrderId, String status, String error);

    /**
     * APP 주문번호와 OMS가 돌려준 값들의 매핑 (§4.4.1).
     *
     * <p>주문번호는 둘이 다른 번호이고, 수령번호는 OMS가 만든 것을
     * 받아 적는 것입니다 — 손님 화면이 주문내역에서 보여주려면
     * 원장에 있어야 합니다.
     */
    void mapToOms(long appOrderId, String omsOrderNo, String pickupNo, String pickupPin);

    /** OMS 가 돌려준 영수증 QR 토큰 (V4). 이력에서 주문 상태 화면으로 가는 열쇠입니다. */
    default void saveClaimToken(long appOrderId, String claimToken) {
    }

    Optional<AppOrder> find(long id);

    /**
     * 그 매장 그 판매일자의 주문들. {@code channel} 이 null 이면 전부입니다.
     *
     * <p>매장을 안 주면 전 매장입니다 — BO의 조회 화면이 "전체"를
     * 고를 수 있어야 해서입니다.
     */
    List<AppOrder> findByDay(String storeId, LocalDate businessDate, OrderChannel channel);

    /**
     * 결제 한 건을 적습니다 (§4.4.2 Payment, §4.10).
     *
     * <p>주문 한 건에 결제도 한 건입니다. 여러 수단으로 나눠 내는 것은
     * POS 매출에는 이미 있지만(TR Payment 가 여러 줄) 손님 채널에는
     * 아직 없습니다 — 그래서 {@code payment_seq} 가 늘 1입니다.
     */
    void savePayment(long appOrderId, String method, java.math.BigDecimal amount,
                     String status, String approvalRef);

    /**
     * 돌려줬습니다 (§4.10).
     *
     * <p>썬앤문페이는 가상이지만 <b>현금과 같은 개념</b>이라 실제로
     * 돌려줍니다. 환불번호를 함께 적는 이유: 무엇을 되돌린 것인지
     * 가리키는 값이 없으면 정산에서 "받은 것"과 "돌려준 것"이 짝을
     * 못 이룹니다.
     *
     * <p>{@code APPROVED} 인 건만 바뀝니다 — 애초에 거절된 결제를
     * 환불로 만들면 갚은 적 없는 돈이 장부에 생깁니다.
     */
    void markRefunded(long appOrderId, String refundNo);

    /**
     * 아직 돌려주지 않은 승인번호. 이미 환불했거나 결제가 없으면 빈 값입니다.
     *
     * <p>{@code APPROVED} 인 것만 돌려줍니다 — 이 조건이 <b>두 번 환불을
     * 막습니다</b>. 손님이 취소를 두 번 누르면 두 번 돌려줄 수도 있는데,
     * 가상 결제라도 그건 장부가 안 맞는 일입니다.
     */
    java.util.Optional<String> unrefundedApprovalOf(long appOrderId);

    /**
     * OMS 와 맞춰 볼 원장 한 줄 (업무 정의서 §5.8, §4.4.6).
     *
     * @param omsOrderNo    OMS 가 접수했을 때 받은 번호 — 전달 실패면 없음
     * @param approvalNo    결제 승인번호(환불됐어도 승인 때의 번호)
     * @param paymentStatus {@code APPROVED} · {@code REFUNDED}, 결제가 없으면 null
     */
    record ChannelCheck(long id, String orderNo, String storeId, LocalDate businessDate, AppOrderStatus status,
                        String omsOrderNo, String approvalNo, String paymentStatus, java.time.Instant updatedAt) {
    }

    /**
     * 매장마다 원장의 최근 영업일 둘(오늘·어제) 안에서, OMS 와 어긋날 수 있는 상태의 주문
     * (SENT · SEND_FAILED · REJECTED · CANCELLED).
     *
     * @param storeId null 이면 전 매장
     */
    default List<ChannelCheck> recentForChannelCheck(String storeId) {
        return List.of();
    }
}
