package com.sunmoon.appserver.domain.member;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 회원 기기 — 토큰은 해시로만 (customer-app.md §3.3). */
public interface MemberDeviceRepository {

    void save(String memberNo, String tokenHash, String platform);

    /** 살아 있는 기기의 회원번호. */
    Optional<String> memberOf(String tokenHash);

    void touch(String tokenHash, Instant at);

    /** @return 끝냈으면 참 */
    boolean revoke(String tokenHash, Instant at);

    /** 같은 푸시 토큰은 한 기기에만 — 다른 기기에 붙어 있던 것은 떼고 붙입니다. */
    boolean setPushToken(String tokenHash, String pushToken);

    /** 회원의 살아 있는 기기들의 푸시 토큰 (A4). */
    List<String> pushTokens(String memberNo);

    /** FCM 이 "없는 토큰" 이라고 한 것 — 앱이 지워진 기기. */
    default void clearPushToken(String pushToken) {
    }
}
