package com.sunmoon.appserver.domain.reprocess;

import com.sunmoon.appserver.domain.order.AppOrderRepository;
import com.sunmoon.appserver.domain.order.AppOrderRepository.ChannelCheck;
import com.sunmoon.appserver.domain.order.OmsGateway;
import com.sunmoon.common.reprocess.FailedItem;
import com.sunmoon.common.reprocess.Reprocessor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code CHANNEL_MISMATCH} — OMS 와 채널(이 원장)의 상태가 어긋난 주문 (업무 정의서 §5.8, §4.4.6).
 *
 * <p>원장은 "손님의 요청이 어떻게 됐나", OMS 는 "매장이 어디까지 했나" 입니다. 둘이 어긋나면 <b>돈과 음식이 어긋납니다</b>:
 * <ol>
 *   <li>원장은 실패·거절(환불)인데 OMS 에 같은 승인번호의 주문이 살아 있음 — 돈은 돌려줬는데 매장이 만들고 있을 수 있음.
 *       OMS 가 주문을 저장하고 응답만 실패한 경우입니다(Order 의 이벤트 발행 실패가 500 으로 오는 것 등).</li>
 *   <li>원장은 SENT 인데 OMS 에 그 주문이 없거나 거절·만료·취소됨 — 매장은 안 받았는데 돈은 받은 채.</li>
 *   <li>원장은 손님 취소인데 OMS 주문이 살아 있음.</li>
 * </ol>
 *
 * <p><b>찾아내기만 합니다</b>({@link #retryable()} 거짓). 환불할지 매장에 알릴지는 사람이 정합니다 — 이 서버가 짐작으로
 * 돈을 움직이면 안 됩니다. 범위는 매장마다 원장의 최근 영업일 둘(오늘·어제)이고, OMS 는 {@code GET /orders/digest} 로
 * <b>한 번</b> 읽습니다. 못 읽으면 빈 목록이 아니라 "비교하지 못했습니다" 한 줄을 냅니다.
 */
@Component
public class ChannelMismatches implements Reprocessor {

    public static final String KIND = "CHANNEL_MISMATCH";

    /** 매장 쪽에서 끝난(안 만드는) 상태. */
    private static final Set<String> DEAD = Set.of("REJECTED", "EXPIRED", "CANCELLED");

    private final AppOrderRepository orders;
    private final OmsGateway oms;

    public ChannelMismatches(AppOrderRepository orders, OmsGateway oms) {
        this.orders = orders;
        this.oms = oms;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String label() {
        return "OMS ↔ 채널 상태 불일치";
    }

    @Override
    public boolean retryable() {
        return false;
    }

    @Override
    public List<FailedItem> failed(String storeId) {
        List<ChannelCheck> ledger = orders.recentForChannelCheck(storeId);
        if (ledger.isEmpty()) {
            return List.of();
        }
        LocalDate since = ledger.stream().map(ChannelCheck::businessDate).min(Comparator.naturalOrder()).orElseThrow();
        Optional<List<OmsGateway.Digest>> digest = oms.digestSince(since);
        if (digest.isEmpty()) {
            return List.of(new FailedItem("oms-unreachable", storeId == null ? "" : storeId,
                    "OMS 를 읽지 못해 비교하지 못했습니다",
                    "OMS 주문 요약(GET /orders/digest)에 닿지 못했습니다 — 어긋난 것이 없다는 뜻이 아닙니다",
                    1, Instant.now()));
        }

        Map<String, OmsGateway.Digest> byOrderNo = new HashMap<>();
        Map<String, OmsGateway.Digest> byApproval = new HashMap<>();
        for (OmsGateway.Digest row : digest.get()) {
            if (row.omsOrderNo() != null) {
                byOrderNo.put(row.omsOrderNo(), row);
            }
            if (row.approvalNo() != null && !row.approvalNo().isBlank()) {
                byApproval.put(row.approvalNo(), row);
            }
        }

        List<FailedItem> found = new ArrayList<>();
        for (ChannelCheck row : ledger) {
            String why = mismatch(row, byOrderNo, byApproval);
            if (why != null) {
                found.add(new FailedItem(String.valueOf(row.id()), row.storeId(), summary(row, byApproval), why,
                        1, row.updatedAt()));
            }
        }
        return found;
    }

    /** 어긋났으면 그 말, 아니면 null. */
    static String mismatch(ChannelCheck row, Map<String, OmsGateway.Digest> byOrderNo,
                           Map<String, OmsGateway.Digest> byApproval) {
        String payment = row.paymentStatus() == null ? "결제 없음" : "결제 " + row.paymentStatus();
        switch (row.status()) {
            case SENT -> {
                if (row.omsOrderNo() == null) {
                    return "원장은 SENT 인데 OMS 주문번호가 없습니다 (" + payment + ")";
                }
                OmsGateway.Digest oms = byOrderNo.get(row.omsOrderNo());
                if (oms == null) {
                    return "원장은 SENT 인데 OMS 에 주문 " + row.omsOrderNo() + " 이 없습니다 (" + payment + ")";
                }
                if (DEAD.contains(oms.status())) {
                    return "매장에서 " + oms.status() + " — 원장은 아직 SENT, " + payment
                            + ("APPROVED".equals(row.paymentStatus()) ? " (환불이 필요할 수 있습니다)" : "");
                }
                return null;
            }
            case SEND_FAILED, REJECTED -> {
                OmsGateway.Digest oms = row.approvalNo() == null ? null : byApproval.get(row.approvalNo());
                if (oms != null && !DEAD.contains(oms.status())) {
                    return "원장은 " + row.status() + "(" + payment + ")인데 OMS 주문 " + oms.omsOrderNo()
                            + " 이 " + oms.status() + " — 매장이 만들고 있을 수 있습니다";
                }
                return null;
            }
            case CANCELLED -> {
                OmsGateway.Digest oms = row.omsOrderNo() == null ? null : byOrderNo.get(row.omsOrderNo());
                if (oms != null && !DEAD.contains(oms.status())) {
                    return "손님은 취소했는데 OMS 주문 " + oms.omsOrderNo() + " 이 " + oms.status();
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private static String summary(ChannelCheck row, Map<String, OmsGateway.Digest> byApproval) {
        String omsNo = row.omsOrderNo();
        if (omsNo == null && row.approvalNo() != null && byApproval.containsKey(row.approvalNo())) {
            omsNo = byApproval.get(row.approvalNo()).omsOrderNo();
        }
        return "원장 " + row.orderNo() + (omsNo == null ? "" : " · OMS 주문 " + omsNo);
    }

    @Override
    public Outcome retry(String storeId, String id) {
        return new Outcome(false, false, "불일치는 찾아내기만 합니다 — 환불·취소는 사람이 판단합니다");
    }
}
