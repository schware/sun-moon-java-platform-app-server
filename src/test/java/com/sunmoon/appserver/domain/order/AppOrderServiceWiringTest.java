package com.sunmoon.appserver.domain.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunmoon.appserver.domain.entry.BusinessDays;
import com.sunmoon.appserver.domain.entry.TableEntryService;
import com.sunmoon.appserver.domain.member.MemberService;
import com.sunmoon.appserver.domain.menu.MenuService;
import com.sunmoon.appserver.domain.payment.AcceptedMethods;
import com.sunmoon.appserver.domain.payment.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Spring 이 {@link AppOrderService} 를 만들 수 있는가.
 *
 * <p>이 계열은 "시험은 전부 통과하고 배포에서만 죽는" 배선 사고를 세 번
 * 겪었습니다(BusinessDayService, MenuService, MemberService). 이 서비스는
 * 2026-09-27에 의존성이 또 하나 늘었고, 나머지 시험은 전부 {@code new}
 * 로 만들어서 빈 정의를 아무도 안 건드립니다. 네 번째가 되지 않게.
 */
class AppOrderServiceWiringTest {

    @Test
    void springCanConstructTheService() {
        new ApplicationContextRunner()
                .withBean(AppOrderRepository.class, () -> mock(AppOrderRepository.class))
                .withBean(MenuService.class, () -> mock(MenuService.class))
                .withBean(TableEntryService.class, () -> mock(TableEntryService.class))
                .withBean(BusinessDays.class, () -> mock(BusinessDays.class))
                .withBean(OmsGateway.class, () -> mock(OmsGateway.class))
                .withBean(PaymentGateway.class, () -> mock(PaymentGateway.class))
                .withBean(AcceptedMethods.class, () -> mock(AcceptedMethods.class))
                .withBean(MemberService.class, () -> mock(MemberService.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(AppOrderService.class)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(AppOrderService.class));
    }
}
