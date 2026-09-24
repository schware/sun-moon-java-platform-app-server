package com.sunmoon.appserver.domain.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 회원번호를 내주고 확인합니다 (업무 정의서 §4.1).
 *
 * <p>손님이 지금 익명입니다 — 주문마다 {@code guest-xxxxxx} 를 지어
 * 붙이고 있어서, 같은 손님이 두 번 시켜도 다른 사람으로 보입니다.
 * 이 번호가 그 자리를 채웁니다.
 *
 * <p><b>회원가입이 아닙니다.</b> 아무것도 묻지 않고 번호만 내줍니다.
 * 손님은 그 번호를 적어 두었다가 다음에 칩니다 — 우리가 아는 것은
 * "같은 번호를 댄 사람"까지이고, 그 이상을 알 이유가 없습니다.
 */
@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    /**
     * 날짜가 번호 안에 들어가므로 <b>어느 시간대의 날짜인지</b>가 값의
     * 일부입니다. 이 플랫폼의 날짜는 전부 Asia/Seoul 입니다
     * (umbrella docs/adr/0006) — 자정 넘어 장사하는 매장이 UTC 로
     * 세면 하루가 둘로 갈립니다.
     */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MemberRepository members;
    private final Clock clock;

    public MemberService(MemberRepository members) {
        this(members, Clock.system(SEOUL));
    }

    MemberService(MemberRepository members, Clock clock) {
        this.members = members;
        this.clock = clock;
    }

    /**
     * 새 번호를 하나 냅니다.
     *
     * <p>영업일자가 아니라 <b>달력 날짜</b>로 셉니다. 영업일자는 매장마다
     * 다른데 회원은 매장에 속하지 않습니다 — 어느 매장에서 받았든 같은
     * 손님이고, 매장별로 세면 같은 사람이 매장 수만큼 번호를 갖게
     * 됩니다.
     */
    public Member issue() {
        LocalDate today = LocalDate.now(clock);
        int seq = members.nextSeq(today);
        Member member = new Member(MemberNo.of(today, seq), today, seq, clock.instant());
        members.save(member);
        log.info("회원번호 발급 {} ({}일 {}번째)", member.memberNo(), today, seq);
        return member;
    }

    /**
     * 우리가 준 번호인가.
     *
     * <p>모양이 아니면 <b>표에 묻지 않습니다</b>. 검증숫자가 그 앞에
     * 서서 오타를 거르고, 그래서 손님이 한 자리 잘못 친 경우에는
     * 데이터베이스까지 가지 않습니다.
     */
    public Optional<Member> verify(String memberNo) {
        if (!MemberNo.looksValid(memberNo)) {
            return Optional.empty();
        }
        return members.find(memberNo);
    }

    /**
     * 주문에 붙일 손님 식별값.
     *
     * <p>번호를 안 댔거나 우리 번호가 아니면 <b>빈 값</b>을 돌려주고,
     * 부르는 쪽이 익명 손님으로 처리합니다. 여기서 막지 않는 이유:
     * 회원번호는 편의이지 자격이 아닙니다 — 번호를 잘못 친 손님이
     * 주문을 못 하게 되면, 그건 도우려고 넣은 값이 장사를 막는
     * 것입니다.
     */
    public Optional<String> customerIdFor(String memberNo) {
        if (memberNo == null || memberNo.isBlank()) {
            return Optional.empty();
        }
        Optional<Member> found = verify(memberNo);
        if (found.isEmpty()) {
            log.info("모르는 회원번호로 주문이 들어왔습니다 — 익명으로 처리합니다");
        }
        return found.map(Member::memberNo);
    }
}
