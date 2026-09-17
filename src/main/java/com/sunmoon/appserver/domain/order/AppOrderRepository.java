package com.sunmoon.appserver.domain.order;

import java.time.LocalDate;
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

    Optional<AppOrder> find(long id);
}
