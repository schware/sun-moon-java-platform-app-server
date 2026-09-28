package com.sunmoon.appserver.domain.push;

import com.sunmoon.appserver.domain.member.MemberDeviceRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 주문 상태 → 손님 휴대폰 (customer-app.md §4). */
class PushServiceTest {

    private static final String MEMBER = com.sunmoon.appserver.domain.member.MemberNo.of(java.time.LocalDate.of(2026, 9, 29), 17);
    private final List<String> sentBodies = new ArrayList<>();
    private final List<String> cleared = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>(List.of("phone-a", "gone-b"));

    private final MemberDeviceRepository devices = new MemberDeviceRepository() {
        @Override public void save(String m, String h, String p) { }
        @Override public Optional<String> memberOf(String h) { return Optional.empty(); }
        @Override public void touch(String h, Instant at) { }
        @Override public boolean revoke(String h, Instant at) { return false; }
        @Override public boolean setPushToken(String h, String t) { return true; }
        @Override public List<String> pushTokens(String memberNo) { return MEMBER.equals(memberNo) ? tokens : List.of(); }
        @Override public void clearPushToken(String t) { cleared.add(t); }
    };

    private final PushSender sender = (token, message) -> {
        if (token.startsWith("gone")) return PushSender.Result.UNREGISTERED;
        sentBodies.add(message.title() + " | " + message.body() + " | " + message.data());
        return PushSender.Result.SENT;
    };

    private PushService service(boolean refunded) {
        return new PushService(no -> Optional.of(new PushComposer.Order("1매장", "P-4", "claim-123", refunded)),
                devices, sender);
    }

    private static String event(String customer, String status, String previous) {
        return "{\"orderId\":77,\"customerId\":\"" + customer + "\",\"status\":\"" + status
                + "\",\"previousStatus\":" + (previous == null ? "null" : "\"" + previous + "\"") + "}";
    }

    @Test
    void readyGoesToEveryDeviceOfTheMemberWithTheClaimToken() {
        int sent = service(false).onEvent(event(MEMBER, "PRODUCED", "COOKING"));

        assertThat(sent).isEqualTo(1);
        assertThat(sentBodies.get(0)).contains("준비됐습니다").contains("P-4").contains("claim-123");
        assertThat(cleared).as("앱이 지워진 기기의 토큰은 지웁니다").containsExactly("gone-b");
    }

    @Test
    void thePinIsNeverInTheNotification() {
        service(false).onEvent(event(MEMBER, "ACCEPTED", "PLACED"));
        assertThat(sentBodies.get(0)).doesNotContain("pickupPin").doesNotContainPattern("\\b\\d{4}\\b");
    }

    @Test
    void refundWordsOnlyWhenActuallyRefunded() {
        service(false).onEvent(event(MEMBER, "REJECTED", "PLACED"));
        service(true).onEvent(event(MEMBER, "REJECTED", "PLACED"));
        assertThat(sentBodies.get(0)).doesNotContain("돌려드렸습니다");
        assertThat(sentBodies.get(1)).contains("돌려드렸습니다");
    }

    @Test
    void anonymousOrdersAndRecallsSendNothing() {
        assertThat(service(false).onEvent(event("guest", "PRODUCED", "COOKING"))).isZero();
        assertThat(service(false).onEvent(event(MEMBER, "PRODUCED", "PRODUCED")))
                .as("다시 부르기 — 상태가 안 바뀜").isZero();
        assertThat(service(false).onEvent("not json")).isZero();
    }
}
