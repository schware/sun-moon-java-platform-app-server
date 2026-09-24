package com.sunmoon.appserver.domain.member;

import java.time.LocalDate;
import java.util.Optional;

/** 발급된 회원번호를 적고 찾습니다 (업무 정의서 §4.1). */
public interface MemberRepository {

    /**
     * 그날의 다음 순번. <b>올리고 돌려받습니다</b> — count(*)+1 로 뽑으면
     * 두 손님이 같은 순간에 받을 때 같은 번호가 납니다.
     */
    int nextSeq(LocalDate businessDate);

    void save(Member member);

    Optional<Member> find(String memberNo);
}
