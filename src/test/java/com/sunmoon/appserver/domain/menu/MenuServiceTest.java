package com.sunmoon.appserver.domain.menu;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MenuServiceTest {

    /** 2026-09-15T23:00Z 는 서울에서는 이미 2026-09-16 아침 8시입니다. */
    private static final Instant MORNING_IN_SEOUL = Instant.parse("2026-09-15T23:00:00Z");

    private final RecordingRepository repository = new RecordingRepository();

    private MenuService serviceAt(Instant now) {
        return new MenuService(repository, Clock.fixed(now, MenuService.STORE_ZONE));
    }

    @Test
    void defaultsToTheStoresCalendarNotTheHostsUtcDate() {
        serviceAt(MORNING_IN_SEOUL).menus(null);

        // UTC로는 아직 15일이지만, 가격은 가게 달력으로 물어봐야 합니다.
        assertThat(repository.asked).isEqualTo(LocalDate.of(2026, 9, 16));
    }

    @Test
    void keepsTheDateItWasGiven() {
        serviceAt(MORNING_IN_SEOUL).menus(LocalDate.of(2026, 9, 1));

        assertThat(repository.asked).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void aMenuWithNoPriceThatDayIsNotOrderable() {
        Menu priced = new Menu("1메뉴", "1메뉴", null, null, "kds-01", new BigDecimal("6000"));
        Menu unpriced = new Menu("2메뉴", "2메뉴", null, null, "kds-01", null);

        // 목록에서 빠지는 게 아니라, 나오되 주문할 수 없어야 합니다 —
        // 안 보이면 "메뉴가 왜 사라졌지"를 디버깅하게 됩니다.
        assertThat(priced.orderable()).isTrue();
        assertThat(unpriced.orderable()).isFalse();
    }

    private static final class RecordingRepository implements MenuRepository {
        private LocalDate asked;

        @Override
        public List<Menu> findAll(LocalDate on) {
            this.asked = on;
            return List.of();
        }

        @Override
        public Optional<Menu> find(String menuCode, LocalDate on) {
            this.asked = on;
            return Optional.empty();
        }
    }
}
