package com.sunmoon.appserver.domain.push;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 주문 상태 → 손님 휴대폰 알림 (umbrella docs/design/customer-app.md §4.2).
 *
 * <ul>
 *   <li><b>PIN 은 절대 넣지 않습니다</b> — 잠금 화면에 뜹니다.</li>
 *   <li>데이터에 {@code claimToken} — 누르면 그 주문 상태 화면으로.</li>
 *   <li>"결제는 돌려드렸습니다" 는 <b>실제로 환불됐을 때만</b>.</li>
 *   <li>다시 부르기는 상태가 안 바뀌어 이벤트가 없습니다 — 보내지 않습니다.</li>
 * </ul>
 */
public final class PushComposer {

    private PushComposer() {
    }

    public record Message(String title, String body, Map<String, String> data) {
    }

    /** 알림에 쓸 주문의 값 — 원장에서. */
    public record Order(String storeName, String pickupNo, String claimToken, boolean refunded) {
    }

    public static Optional<Message> of(String status, String previousStatus, Order order) {
        if (status == null || status.equals(previousStatus)) {
            return Optional.empty();   // 다시 부르기 등 — 상태가 안 바뀐 이벤트
        }
        String number = order.pickupNo() == null ? "" : "수령번호 " + order.pickupNo();
        String store = order.storeName() == null ? "" : order.storeName();
        Message message = switch (status) {
            case "ACCEPTED" -> new Message("주문이 접수됐습니다", join(store, number), data(order));
            case "PRODUCED" -> new Message("준비됐습니다",
                    (number.isEmpty() ? "" : number + " — ") + "카운터로 오세요", data(order));
            case "REJECTED", "EXPIRED" -> new Message("주문이 취소됐습니다",
                    order.refunded() ? "결제는 돌려드렸습니다" : "매장이 주문을 받지 못했습니다", data(order));
            case "CANCELLED" -> new Message("취소됐습니다",
                    order.refunded() ? "결제는 돌려드렸습니다" : "주문이 취소됐습니다", data(order));
            default -> null;
        };
        return Optional.ofNullable(message);
    }

    private static String join(String a, String b) {
        return a.isEmpty() ? b : b.isEmpty() ? a : a + " · " + b;
    }

    private static Map<String, String> data(Order order) {
        Map<String, String> data = new LinkedHashMap<>();
        if (order.claimToken() != null) {
            data.put("claimToken", order.claimToken());
        }
        return data;
    }
}
