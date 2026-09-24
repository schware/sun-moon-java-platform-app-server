package com.sunmoon.appserver.domain.member;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 회원번호를 만들고 읽는 규칙 한 곳 (업무 정의서 §4.1).
 *
 * <p>Sun&amp;Moon이 2026-09-10에 정한 모양입니다:
 * <b>날짜 + 그날 들어온 순번 + 검증숫자</b>.
 *
 * <pre>
 *   260924  0007  3
 *   ──────  ────  ─
 *   날짜    순번  검증
 * </pre>
 *
 * <h2>왜 전화번호가 아닌가</h2>
 *
 * <p>전화번호는 개인정보입니다. 받는 순간 보관 기간과 파기 절차와
 * 유출 시의 책임이 따라오는데, 지금 필요한 것은 "같은 손님인가"
 * 하나뿐입니다. 회원가입도 같은 이유로 뺐습니다 — 필요한 것에 비해
 * 너무 큽니다.
 *
 * <h2>검증숫자가 하는 일과 안 하는 일</h2>
 *
 * <p>Luhn 입니다. 카드번호와 같은 방식이고, <b>한 자리를 잘못 친 것과
 * 이웃한 두 자리를 바꿔 친 것</b>을 거의 다 잡습니다. 손님이 종이를
 * 보고 열한 자리를 옮겨 치는 상황에서 실제로 일어나는 오류가 그
 * 둘입니다.
 *
 * <p><b>지어낸 번호는 못 잡습니다.</b> 열한 자리 중 열 자리를 맞게
 * 지으면 통과합니다 — 그건 이 값이 하는 일이 아니고, 그래서 발급된
 * 번호를 표에 적어 둡니다. 검증숫자는 데이터베이스에 묻기 <i>전에</i>
 * 오타를 걸러 주는 값입니다.
 *
 * <h2>숫자만인 이유</h2>
 *
 * <p>손님이 휴대폰 키패드로 칩니다. 글자가 섞이면 자판을 바꿔야 하고,
 * 그 한 번이 "그냥 안 쓸래"가 됩니다.
 */
public final class MemberNo {

    /**
     * 여섯 자리 날짜. {@code yyyy} 를 안 쓰는 이유는 길이입니다 —
     * 손님이 옮겨 치는 값이라 두 자리가 아깝습니다. 2100년에 다시
     * 겹치지만, 그때 이 번호를 되짚을 일은 없습니다.
     */
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    /**
     * 네 자리. 하루 만 명이 넘으면 자리가 늘어날 뿐 깨지지 않습니다 —
     * 0을 채우는 것은 자릿수를 맞추려는 것이지 상한을 두는 것이
     * 아닙니다.
     */
    private static final int SEQ_DIGITS = 4;

    /** 날짜 6 + 순번 4 + 검증 1. */
    public static final int LENGTH = 11;

    private MemberNo() {
    }

    public static String of(LocalDate businessDate, int seq) {
        String body = DAY.format(businessDate) + String.format("%0" + SEQ_DIGITS + "d", seq);
        return body + checkDigit(body);
    }

    /**
     * 모양과 검증숫자가 맞는가. <b>발급된 번호인지는 묻지 않습니다</b> —
     * 그건 표가 답할 질문이고, 이 검사는 그 앞에 섭니다.
     */
    public static boolean looksValid(String memberNo) {
        if (memberNo == null || memberNo.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < LENGTH; i++) {
            if (memberNo.charAt(i) < '0' || memberNo.charAt(i) > '9') {
                return false;
            }
        }
        String body = memberNo.substring(0, LENGTH - 1);
        return checkDigit(body) == memberNo.charAt(LENGTH - 1);
    }

    /**
     * Luhn 검증숫자.
     *
     * <p>오른쪽부터 한 칸 걸러 두 배로 만들고, 두 배가 9를 넘으면 9를
     * 뺍니다(자릿수 합과 같습니다). 그 총합을 10으로 채우는 수가
     * 검증숫자입니다.
     */
    private static char checkDigit(String body) {
        int sum = 0;
        boolean twice = true; // 검증숫자 자리에서 왼쪽으로 세므로 맨 오른쪽이 두 배입니다
        for (int i = body.length() - 1; i >= 0; i--) {
            int digit = body.charAt(i) - '0';
            if (twice) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            twice = !twice;
        }
        return (char) ('0' + ((10 - (sum % 10)) % 10));
    }
}
