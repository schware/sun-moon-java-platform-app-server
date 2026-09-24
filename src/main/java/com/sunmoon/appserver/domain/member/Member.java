package com.sunmoon.appserver.domain.member;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 회원번호 한 줄 (업무 정의서 §4.1).
 *
 * <p><b>개인정보가 없습니다.</b> 이름도 전화번호도 없고, 있는 것은
 * 번호와 언제 났는지뿐입니다. 그게 이 설계의 요점입니다 — 같은 손님인지
 * 알 수 있으면 충분하고, 누구인지까지 알 이유가 없습니다.
 */
public record Member(String memberNo, LocalDate businessDate, int seq, Instant issuedAt) {
}
