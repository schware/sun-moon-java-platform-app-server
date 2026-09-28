package com.sunmoon.appserver.domain.member;

import com.sunmoon.appserver.domain.order.OmsGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 회원 토큰 — 이력을 볼 자격 (customer-app.md §3). */
class MemberDevicesTest {

    private Instant now = Instant.parse("2026-09-29T01:00:00Z");
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    };

    /** 해시 → (회원번호, 끝났나) */
    private final Map<String, String[]> rows = new HashMap<>();
    private final MemberDeviceRepository repo = new MemberDeviceRepository() {
        @Override public void save(String memberNo, String tokenHash, String platform) {
            rows.put(tokenHash, new String[] {memberNo, null});
        }
        @Override public Optional<String> memberOf(String tokenHash) {
            String[] r = rows.get(tokenHash);
            return r == null || r[1] != null ? Optional.empty() : Optional.of(r[0]);
        }
        @Override public void touch(String tokenHash, Instant at) { }
        @Override public boolean revoke(String tokenHash, Instant at) {
            String[] r = rows.get(tokenHash);
            if (r == null || r[1] != null) return false;
            r[1] = "revoked";
            return true;
        }
        @Override public boolean setPushToken(String tokenHash, String pushToken) { return true; }
        @Override public List<String> pushTokens(String memberNo) { return new ArrayList<>(); }
    };

    private static final String MEMBER = "26092900017";
    private static final String RECEIPT = "abcdefghijklmnopqrstuv";

    private final MemberService members = mock(MemberService.class);
    private final OmsGateway oms = mock(OmsGateway.class);
    private final MemberDevices devices = new MemberDevices(repo, members, oms, clock);

    MemberDevicesTest() {
        when(members.verify(MEMBER)).thenReturn(Optional.of(
                new Member(MEMBER, java.time.LocalDate.of(2026, 9, 29), 17, now)));
        when(oms.claimOwner(RECEIPT)).thenReturn(Optional.of(MEMBER));
    }

    @Test
    void theTokenIsTheRightToSeeHistoryNotTheNumber() {
        String token = devices.issueToken(MEMBER, "android");
        assertThat(devices.memberOf("Member " + token)).contains(MEMBER);
        assertThat(devices.memberOf("Member " + MEMBER)).as("번호는 토큰이 아닙니다").isEmpty();
        assertThat(devices.memberOf(null)).isEmpty();
        assertThat(rows.keySet()).doesNotContain(token).as("토큰 자체는 저장하지 않습니다");
    }

    @Test
    void aReceiptOfThatNumberLinksANewDevice() {
        MemberDevices.Link link = devices.link(MEMBER, RECEIPT, "WEB");
        assertThat(link.result()).isEqualTo(MemberDevices.LinkResult.LINKED);
        assertThat(devices.memberOf("Member " + link.token())).contains(MEMBER);
    }

    @Test
    void someoneElsesReceiptDoesNot() {
        when(oms.claimOwner("zzzzzzzzzzzzzzzzzzzzzz")).thenReturn(Optional.of("26092900099"));
        assertThat(devices.link(MEMBER, "zzzzzzzzzzzzzzzzzzzzzz", "WEB").result())
                .isEqualTo(MemberDevices.LinkResult.NOT_OURS);
    }

    @Test
    void guessingIsLockedForAnHour() {
        for (int i = 0; i < MemberDevices.MAX_LINK_FAILURES; i++) {
            devices.link(MEMBER, "wrong-receipt-" + i, "WEB");
        }
        assertThat(devices.link(MEMBER, RECEIPT, "WEB").result())
                .as("맞는 영수증도 잠긴 동안은").isEqualTo(MemberDevices.LinkResult.LOCKED);
        now = now.plus(Duration.ofMinutes(61));
        assertThat(devices.link(MEMBER, RECEIPT, "WEB").result()).isEqualTo(MemberDevices.LinkResult.LINKED);
    }

    @Test
    void logoutEndsOnlyThisDevice() {
        String a = devices.issueToken(MEMBER, "WEB");
        String b = devices.issueToken(MEMBER, "ANDROID");
        assertThat(devices.logout("Member " + a)).isTrue();
        assertThat(devices.memberOf("Member " + a)).isEmpty();
        assertThat(devices.memberOf("Member " + b)).contains(MEMBER);
    }
}
