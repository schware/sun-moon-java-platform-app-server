package com.sunmoon.appserver.domain.reprocess;

import com.sunmoon.appserver.domain.order.AppOrderRepository;
import com.sunmoon.appserver.domain.order.AppOrderRepository.ChannelCheck;
import com.sunmoon.appserver.domain.order.AppOrderStatus;
import com.sunmoon.appserver.domain.order.OmsGateway;
import com.sunmoon.common.reprocess.FailedItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** OMS ↔ 채널 정합성 (업무 정의서 §5.8, §4.4.6) — 돈과 음식이 어긋나는 셋, 그리고 어긋나지 않은 것들. */
class ChannelMismatchesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final Instant AT = Instant.parse("2026-10-08T03:00:00Z");

    private final AppOrderRepository orders = mock(AppOrderRepository.class);
    private final OmsGateway oms = mock(OmsGateway.class);
    private final ChannelMismatches mismatches = new ChannelMismatches(orders, oms);

    private static ChannelCheck row(long id, AppOrderStatus status, String omsNo, String approval, String payment) {
        return new ChannelCheck(id, "S01-20261008-" + id, "store-01", TODAY, status, omsNo, approval, payment, AT);
    }

    private static OmsGateway.Digest oms(String no, String status, String approval) {
        return new OmsGateway.Digest(no, "store-01", TODAY, status, approval);
    }

    @Test
    void findsTheThreeWaysMoneyAndFoodDisagree() {
        when(orders.recentForChannelCheck(null)).thenReturn(List.of(
                // ① 환불했는데 매장 주문이 살아 있음
                row(1, AppOrderStatus.SEND_FAILED, null, "A-1", "REFUNDED"),
                // ② SENT 인데 매장이 거절
                row(2, AppOrderStatus.SENT, "102", "A-2", "APPROVED"),
                // ② SENT 인데 OMS 에 없음
                row(3, AppOrderStatus.SENT, "103", "A-3", "APPROVED"),
                // ③ 손님 취소인데 매장 주문이 조리 중
                row(4, AppOrderStatus.CANCELLED, "104", "A-4", "REFUNDED")));
        when(oms.digestSince(TODAY)).thenReturn(Optional.of(List.of(
                oms("101", "ACCEPTED", "A-1"),
                oms("102", "REJECTED", "A-2"),
                oms("104", "COOKING", "A-4"))));

        List<FailedItem> items = mismatches.failed(null);

        assertThat(items).extracting(FailedItem::id).containsExactly("1", "2", "3", "4");
        assertThat(items.get(0).summary()).isEqualTo("원장 S01-20261008-1 · OMS 주문 101");
        assertThat(items.get(0).error()).contains("SEND_FAILED(결제 REFUNDED)인데 OMS 주문 101 이 ACCEPTED");
        assertThat(items.get(1).error()).contains("매장에서 REJECTED").contains("환불이 필요할 수 있습니다");
        assertThat(items.get(2).error()).contains("OMS 에 주문 103 이 없습니다");
        assertThat(items.get(3).error()).contains("손님은 취소했는데 OMS 주문 104 이 COOKING");
    }

    @Test
    void agreeingOrdersAreNotListed() {
        when(orders.recentForChannelCheck("store-01")).thenReturn(List.of(
                row(5, AppOrderStatus.SENT, "105", "A-5", "APPROVED"),
                row(6, AppOrderStatus.SEND_FAILED, null, "A-6", "REFUNDED"),     // OMS 에도 없음 — 맞음
                row(7, AppOrderStatus.REJECTED, null, "A-7", "REFUNDED"),        // OMS 도 거절 — 맞음
                row(8, AppOrderStatus.CANCELLED, "108", "A-8", "REFUNDED")));    // OMS 도 취소 — 맞음
        when(oms.digestSince(TODAY)).thenReturn(Optional.of(List.of(
                oms("105", "COOKING", "A-5"),
                oms("107", "REJECTED", "A-7"),
                oms("108", "CANCELLED", "A-8"))));

        assertThat(mismatches.failed("store-01")).isEmpty();
    }

    @Test
    void readsOmsOnceFromTheOldestLedgerDay() {
        when(orders.recentForChannelCheck(null)).thenReturn(List.of(
                new ChannelCheck(9, "S01-20261007-9", "store-01", YESTERDAY, AppOrderStatus.SENT, "109", "A-9",
                        "APPROVED", AT),
                row(10, AppOrderStatus.SENT, "110", "A-10", "APPROVED")));
        when(oms.digestSince(YESTERDAY)).thenReturn(Optional.of(List.of(
                oms("109", "COMPLETED", "A-9"), oms("110", "PLACED", "A-10"))));

        assertThat(mismatches.failed(null)).isEmpty();
        verify(oms).digestSince(YESTERDAY);
    }

    @Test
    void unreachableOmsIsShownNotHidden() {
        when(orders.recentForChannelCheck(null)).thenReturn(List.of(row(1, AppOrderStatus.SENT, "101", "A-1", "APPROVED")));
        when(oms.digestSince(any())).thenReturn(Optional.empty());

        List<FailedItem> items = mismatches.failed(null);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).summary()).isEqualTo("OMS 를 읽지 못해 비교하지 못했습니다");
        assertThat(items.get(0).error()).contains("어긋난 것이 없다는 뜻이 아닙니다");
    }

    @Test
    void anEmptyLedgerDoesNotEvenAskOms() {
        when(orders.recentForChannelCheck(null)).thenReturn(List.of());

        assertThat(mismatches.failed(null)).isEmpty();
        verify(oms, never()).digestSince(any());
    }

    @Test
    void findOnly() {
        assertThat(mismatches.retryable()).isFalse();
        assertThat(mismatches.retry(null, "1").accepted()).isFalse();
    }
}
