package com.sunmoon.appserver.domain.member;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link com.sunmoon.appserver.domain.menu.MenuServiceWiringTest} 와 같은
 * 것이고, 같은 이유로 있습니다.
 *
 * <p>이 계열은 <b>생성자가 둘인데 {@code @Autowired} 가 없는 서비스</b>로
 * 이미 두 번 죽었습니다 — Order 의 {@code BusinessDayService}, 그리고 이
 * 서비스의 {@code MenuService}. {@code MemberService} 가 2026-09-24에
 * 세 번째였습니다.
 *
 * <p>매번 같은 모양입니다: 나머지 테스트는 서비스를 직접 {@code new} 해서
 * 쓰므로 빈 정의를 아무도 안 건드리고, <b>테스트를 전부 통과한 채
 * 배포에서 죽습니다.</b> 이번에는 실제로 운영 중인 컨테이너가 재시작을
 * 반복했습니다.
 *
 * <p>데이터베이스도 웹 계층도 없이 "Spring 이 이걸 만들 수 있나"만
 * 물어봅니다. 이 버그를 잡는 가장 싼 방법입니다.
 */
class MemberServiceWiringTest {

    @Test
    void springCanConstructTheService() {
        new ApplicationContextRunner()
                .withBean(MemberRepository.class, () -> mock(MemberRepository.class))
                .withBean(MemberService.class)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(MemberService.class));
    }
}
