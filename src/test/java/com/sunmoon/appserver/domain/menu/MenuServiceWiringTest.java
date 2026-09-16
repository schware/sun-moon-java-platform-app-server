package com.sunmoon.appserver.domain.menu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 나머지 테스트는 전부 서비스를 직접 만들어 쓰기 때문에, 실제 빈 정의를
 * 건드리는 것이 하나도 없었습니다. 그래서 생성자가 둘이고 {@code @Autowired}가
 * 없는 서비스가 **테스트를 전부 통과하고 배포에서 죽었습니다.**
 *
 * <p>Order의 {@code BusinessDayServiceWiringTest}와 같은 것이고, 같은 이유로
 * 있습니다 — 한 계열에서 같은 버그가 두 번 났으면 검사도 두 곳에 있어야
 * 합니다. 데이터베이스도 웹 계층도 없이 "Spring이 이걸 만들 수 있나"만
 * 물어보는, 이 버그를 잡는 가장 싼 방법입니다.
 */
class MenuServiceWiringTest {

    @Test
    void springCanConstructTheService() {
        new ApplicationContextRunner()
                .withBean(MenuRepository.class, () -> mock(MenuRepository.class))
                .withBean(MenuService.class)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(MenuService.class));
    }
}
