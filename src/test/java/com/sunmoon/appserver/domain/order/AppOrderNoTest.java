package com.sunmoon.appserver.domain.order;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 업무 주문번호의 규칙.
 *
 * <p>이 번호는 손님 화면에 뜨고 나중에 영수증에도 찍힙니다 — 한 번
 * 나간 모양은 못 바꿉니다. 그래서 모양을 검사로 묶어 둡니다.
 */
class AppOrderNoTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 17);

    @Test
    void isStorePlusBusinessDatePlusSequence() {
        assertThat(AppOrderNo.of("store-01", DAY, 1)).isEqualTo("store-01-20260917-0001");
        assertThat(AppOrderNo.of("store-07", DAY, 123)).isEqualTo("store-07-20260917-0123");
    }

    @Test
    void zeroPadsSoTheNumberDoesNotJump() {
        // 자릿수가 들쭉날쭉하면 목록에서 눈으로 훑기가 어렵습니다.
        assertThat(AppOrderNo.of("store-01", DAY, 9)).endsWith("-0009");
        assertThat(AppOrderNo.of("store-01", DAY, 99)).endsWith("-0099");
    }

    @Test
    void aBusyStoreGetsALongerNumberRatherThanAWrongOne() {
        // 0을 채우는 것은 자릿수를 맞추려는 것이지 상한을 두는 것이
        // 아닙니다. 만 건을 넘기면 길어질 뿐 깨지지 않아야 합니다.
        assertThat(AppOrderNo.of("store-01", DAY, 12345)).isEqualTo("store-01-20260917-12345");
    }

    @Test
    void theSameSequenceOnADifferentDayIsADifferentNumber() {
        // 일련번호가 판매일자로 갈리므로 매일 1번부터 다시 시작합니다.
        // 날짜가 번호 안에 없으면 어제 1번과 오늘 1번이 같은 번호입니다.
        assertThat(AppOrderNo.of("store-01", DAY, 1))
                .isNotEqualTo(AppOrderNo.of("store-01", DAY.plusDays(1), 1));
    }
}
