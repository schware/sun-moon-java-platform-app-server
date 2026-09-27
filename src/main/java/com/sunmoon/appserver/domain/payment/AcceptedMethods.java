package com.sunmoon.appserver.domain.payment;

/**
 * 이 브랜드가 <b>받기로 한</b> 결제수단인가 (2026-09-27).
 *
 * <p>{@link PaymentMethod} 는 이 서버가 <b>처리할 줄 아는</b> 방식이고,
 * 이것은 운영자가 BO 기준정보 → 결제수단에서 켜고 끄는 목록입니다. 둘이
 * 한 곳이면 결제수단 하나를 끄려고 배포를 해야 합니다.
 *
 * <h2>답이 셋인 이유</h2>
 *
 * <p>{@code ACCEPTED} / {@code DISABLED} 에 {@code UNKNOWN} 이 하나 더
 * 있습니다. <b>모르는 것과 꺼진 것은 다릅니다</b> — 단말 인증
 * (Device Server 의 BoDeviceDirectory)에서 같은 판단을 했습니다. 목록을
 * 못 읽는다고 결제를 막으면, 기준정보를 읽는 길 하나가 막힌 것이 장사
 * 전체를 멈춥니다. 그래서 모를 때는 받고 크게 경고합니다.
 */
public interface AcceptedMethods {

    enum Acceptance {
        /** 켜져 있습니다. */
        ACCEPTED,
        /** 꺼져 있거나 목록에 없습니다 — 운영자가 받지 않기로 했습니다. */
        DISABLED,
        /** 목록을 읽지 못했습니다. 받되 경고합니다. */
        UNKNOWN
    }

    Acceptance of(PaymentMethod method);
}
