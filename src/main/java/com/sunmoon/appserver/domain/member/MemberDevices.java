package com.sunmoon.appserver.domain.member;

import com.sunmoon.appserver.domain.order.OmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 회원 토큰 — 이력을 볼 자격 (umbrella docs/design/customer-app.md §3).
 *
 * <p>번호는 주문에 붙이는 값이고 비밀이 아닙니다. <b>토큰은 그 번호를 받은 기기만</b> 가지며,
 * 이력은 토큰으로만 봅니다. 다른 기기로 옮길 때의 증명은 <b>영수증</b> — 그 번호로 주문한
 * 영수증의 QR 토큰을 OMS 에 물어, 그 주문의 손님이 이 번호면 새 기기에 토큰을 줍니다.
 *
 * <p>영수증 연결은 번호를 바꿔 가며 맞혀 볼 수 있으므로 ▶ 같은 회원번호로 10분에 5번 틀리면
 * 1시간 막습니다.
 */
@Service
public class MemberDevices {

    private static final Logger log = LoggerFactory.getLogger(MemberDevices.class);
    public static final Set<String> PLATFORMS = Set.of("WEB", "ANDROID", "IOS");
    static final int MAX_LINK_FAILURES = 5;
    static final Duration LINK_WINDOW = Duration.ofMinutes(10);
    static final Duration LINK_LOCK = Duration.ofHours(1);

    private final MemberDeviceRepository devices;
    private final MemberService members;
    private final OmsGateway oms;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Deque<Instant>> linkFailures = new ConcurrentHashMap<>();
    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    @Autowired
    public MemberDevices(MemberDeviceRepository devices, MemberService members, OmsGateway oms) {
        this(devices, members, oms, Clock.systemUTC());
    }

    MemberDevices(MemberDeviceRepository devices, MemberService members, OmsGateway oms, Clock clock) {
        this.devices = devices;
        this.members = members;
        this.oms = oms;
        this.clock = clock;
    }

    static String platformOf(String raw) {
        String p = raw == null ? "WEB" : raw.trim().toUpperCase(Locale.ROOT);
        return PLATFORMS.contains(p) ? p : "WEB";
    }

    /** 이 기기에 토큰을 줍니다. 토큰 자체는 돌려주기만 하고 저장하지 않습니다. */
    public String issueToken(String memberNo, String platform) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        devices.save(memberNo, hash(token), platformOf(platform));
        return token;
    }

    /** {@code Authorization: Member <token>} → 회원번호. */
    public Optional<String> memberOf(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Member ", 0, 7)) {
            return Optional.empty();
        }
        String h = hash(authorization.substring(7).trim());
        Optional<String> member = devices.memberOf(h);
        member.ifPresent(m -> devices.touch(h, clock.instant()));
        return member;
    }

    public boolean logout(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Member ", 0, 7)) {
            return false;
        }
        return devices.revoke(hash(authorization.substring(7).trim()), clock.instant());
    }

    public boolean setPushToken(String authorization, String pushToken) {
        if (authorization == null || pushToken == null || pushToken.isBlank()) {
            return false;
        }
        return devices.setPushToken(hash(authorization.substring(7).trim()), pushToken.trim());
    }

    public enum LinkResult { LINKED, NOT_OURS, LOCKED }

    public record Link(LinkResult result, String token) {
    }

    /** 영수증으로 새 기기를 연결합니다 (§3.2). */
    public Link link(String memberNo, String claimToken, String platform) {
        Instant now = clock.instant();
        String key = memberNo == null ? "" : memberNo;
        Instant locked = lockedUntil.get(key);
        if (locked != null && locked.isAfter(now)) {
            return new Link(LinkResult.LOCKED, null);
        }
        boolean ours = members.verify(memberNo).isPresent()
                && claimToken != null
                && oms.claimOwner(claimToken).filter(memberNo::equals).isPresent();
        if (!ours) {
            Deque<Instant> recent = linkFailures.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (recent) {
                while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(LINK_WINDOW))) {
                    recent.removeFirst();
                }
                recent.addLast(now);
                if (recent.size() >= MAX_LINK_FAILURES) {
                    lockedUntil.put(key, now.plus(LINK_LOCK));
                    recent.clear();
                    log.warn("회원번호 {} 로 영수증 연결이 여러 번 틀려 1시간 막습니다", MemberNo.masked(memberNo));
                }
            }
            return new Link(LinkResult.NOT_OURS, null);
        }
        linkFailures.remove(key);
        log.info("회원번호 {} 를 영수증으로 새 기기에 연결했습니다", MemberNo.masked(memberNo));
        return new Link(LinkResult.LINKED, issueToken(memberNo, platform));
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
