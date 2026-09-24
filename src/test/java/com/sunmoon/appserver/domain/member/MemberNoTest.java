package com.sunmoon.appserver.domain.member;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원번호의 규칙 (업무 정의서 §4.1).
 *
 * <p>검증숫자가 실제로 무엇을 잡는지를 시험합니다. "Luhn 을 넣었다"는
 * 것과 "손님이 한 자리 잘못 쳤을 때 잡힌다"는 다른 말이고, 확인해야
 * 하는 것은 뒤쪽입니다.
 */
class MemberNoTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 24);

    @Test
    void 모양은_날짜_순번_검증숫자다() {
        String no = MemberNo.of(DAY, 7);

        assertThat(no).hasSize(MemberNo.LENGTH);
        assertThat(no).startsWith("260924");
        assertThat(no.substring(6, 10)).isEqualTo("0007");
        assertThat(no).containsOnlyDigits();
    }

    @Test
    void 자기가_만든_번호는_통과한다() {
        for (int seq = 1; seq <= 500; seq++) {
            assertThat(MemberNo.looksValid(MemberNo.of(DAY, seq)))
                    .as("seq=" + seq)
                    .isTrue();
        }
    }

    @Test
    void 한_자리를_잘못_치면_잡힌다() {
        // 손님이 종이를 보고 열한 자리를 옮겨 치는 상황에서 제일 흔한
        // 오류입니다.
        String no = MemberNo.of(DAY, 42);
        for (int i = 0; i < no.length(); i++) {
            for (char d = '0'; d <= '9'; d++) {
                if (d == no.charAt(i)) {
                    continue;
                }
                String typo = no.substring(0, i) + d + no.substring(i + 1);
                assertThat(MemberNo.looksValid(typo))
                        .as("자리 " + i + " 를 " + d + " 로 잘못 침: " + typo)
                        .isFalse();
            }
        }
    }

    @Test
    void 이웃한_두_자리를_바꿔_치면_거의_다_잡힌다() {
        // 두 번째로 흔한 오류입니다. Luhn 은 09 ↔ 90 한 쌍만 못 잡는데,
        // 그건 이 방식의 알려진 한계이고 여기서 확인해 두는 편이
        // 나중에 "왜 이건 통과하지"를 다시 조사하지 않게 합니다.
        String no = MemberNo.of(DAY, 123);
        int missed = 0;
        int swaps = 0;
        for (int i = 0; i + 1 < no.length(); i++) {
            if (no.charAt(i) == no.charAt(i + 1)) {
                continue; // 같은 숫자를 바꿔 치면 같은 번호입니다
            }
            swaps++;
            String swapped = no.substring(0, i) + no.charAt(i + 1) + no.charAt(i) + no.substring(i + 2);
            if (MemberNo.looksValid(swapped)) {
                missed++;
                // 못 잡는 것은 09 ↔ 90 뿐이어야 합니다.
                assertThat("" + no.charAt(i) + no.charAt(i + 1)).isIn("09", "90");
            }
        }
        assertThat(swaps).isGreaterThan(0);
        assertThat(missed).isLessThanOrEqualTo(1);
    }

    @Test
    void 모양이_아닌_것은_거른다() {
        assertThat(MemberNo.looksValid(null)).isFalse();
        assertThat(MemberNo.looksValid("")).isFalse();
        assertThat(MemberNo.looksValid("2609240007")).isFalse();   // 열 자리
        assertThat(MemberNo.looksValid("260924000731")).isFalse(); // 열두 자리
        assertThat(MemberNo.looksValid("26092400O73")).isFalse();  // 0 대신 O
        assertThat(MemberNo.looksValid("' OR 1=1 --")).isFalse();
    }

    @Test
    void 날마다_1번부터_다시_센다() {
        // 번호가 끝없이 길어지지 않게 하는 규칙입니다. 날짜가 앞에
        // 있으므로 순번이 겹쳐도 번호는 안 겹칩니다.
        String today = MemberNo.of(DAY, 1);
        String tomorrow = MemberNo.of(DAY.plusDays(1), 1);

        assertThat(today).isNotEqualTo(tomorrow);
        assertThat(today.substring(6, 10)).isEqualTo(tomorrow.substring(6, 10));
    }
}
