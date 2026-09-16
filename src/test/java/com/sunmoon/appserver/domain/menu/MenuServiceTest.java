package com.sunmoon.appserver.domain.menu;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class MenuServiceTest {

    /** 2026-09-15T23:00Z 는 서울에서는 이미 2026-09-16 아침 8시입니다. */
    private static final Instant MORNING_IN_SEOUL = Instant.parse("2026-09-15T23:00:00Z");

    private final RecordingRepository repository = new RecordingRepository();
    private final FakeQueue queue = new FakeQueue();

    private MenuService serviceAt(Instant now) {
        return new MenuService(repository, queue, Clock.fixed(now, MenuService.STORE_ZONE));
    }

    private static Menu menu(String code, BigDecimal price, Integer cookMinutes, boolean soldOut) {
        return new Menu(code, code, null, null, "kds-01", price, cookMinutes, soldOut);
    }

    @Test
    void defaultsToTheStoresCalendarNotTheHostsUtcDate() {
        serviceAt(MORNING_IN_SEOUL).board("store-01", null);

        // UTC로는 아직 15일이지만, 가격은 가게 달력으로 물어봐야 합니다.
        assertThat(repository.askedDate).isEqualTo(LocalDate.of(2026, 9, 16));
    }

    @Test
    void keepsTheDateItWasGiven() {
        serviceAt(MORNING_IN_SEOUL).board("store-01", LocalDate.of(2026, 9, 1));

        assertThat(repository.askedDate).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void asksForTheStoreItWasGiven() {
        // 결품은 매장마다 다릅니다 — 매장을 안 넘기면 1호점의 품절이
        // 2호점 손님 화면에 뜹니다.
        serviceAt(MORNING_IN_SEOUL).board("store-07", null);

        assertThat(repository.askedStore).isEqualTo("store-07");
    }

    @Test
    void aMenuWithNoPriceThatDayIsNotOrderable() {
        // 목록에서 빠지는 게 아니라, 나오되 주문할 수 없어야 합니다 —
        // 안 보이면 "메뉴가 왜 사라졌지"를 디버깅하게 됩니다.
        assertThat(menu("1메뉴", new BigDecimal("6000"), null, false).orderable()).isTrue();
        assertThat(menu("2메뉴", null, null, false).orderable()).isFalse();
    }

    @Test
    void aSoldOutMenuIsShownButNotOrderable() {
        Menu soldOut = menu("3메뉴", new BigDecimal("8000"), 10, true);

        repository.rows = List.of(soldOut);
        queue.cooking = OptionalInt.of(2);
        MenuBoard board = serviceAt(MORNING_IN_SEOUL).board("store-01", null);

        assertThat(board.menus()).hasSize(1);
        MenuBoard.Item item = board.menus().getFirst();
        assertThat(item.soldOut()).isTrue();
        assertThat(item.orderable()).isFalse();
        // 기다릴 일이 없는 메뉴에 예상시간을 붙이면 "곧 나온다"로 읽힙니다.
        assertThat(item.estimatedMinutes()).isNull();
    }

    @Test
    void theEstimateCountsTheCustomersOwnOrder() {
        repository.rows = List.of(menu("1메뉴", new BigDecimal("6000"), 7, false));
        queue.cooking = OptionalInt.of(0);

        // 대기열이 비어도 조리시간은 걸립니다 — 그냥 곱하면 0분이 나오고,
        // 그건 즉시 나온다는 거짓말입니다.
        assertThat(serviceAt(MORNING_IN_SEOUL).board("store-01", null)
                .menus().getFirst().estimatedMinutes()).isEqualTo(7);

        queue.cooking = OptionalInt.of(3);
        assertThat(serviceAt(MORNING_IN_SEOUL).board("store-01", null)
                .menus().getFirst().estimatedMinutes()).isEqualTo(28);
    }

    @Test
    void anUnknownQueueMeansNoEstimateRatherThanZero() {
        repository.rows = List.of(menu("1메뉴", new BigDecimal("6000"), 7, false));
        queue.cooking = OptionalInt.empty();

        MenuBoard.Item item = serviceAt(MORNING_IN_SEOUL).board("store-01", null).menus().getFirst();
        // OMS가 안 보인다고 메뉴판까지 죽으면 안 됩니다. 메뉴는 나오고
        // 예상시간만 빠집니다.
        assertThat(item.estimatedMinutes()).isNull();
        assertThat(item.orderable()).isTrue();
    }

    @Test
    void aMenuWithNoStandardCookTimeHasNoEstimate() {
        repository.rows = List.of(menu("1메뉴", new BigDecimal("6000"), null, false));
        queue.cooking = OptionalInt.of(5);

        assertThat(serviceAt(MORNING_IN_SEOUL).board("store-01", null)
                .menus().getFirst().estimatedMinutes()).isNull();
    }

    @Test
    void theQueueIsAskedOnceForTheWholeBoardAndNotAtAllWhenEmpty() {
        repository.rows = List.of(
                menu("1메뉴", new BigDecimal("6000"), 5, false),
                menu("2메뉴", new BigDecimal("7000"), 5, false),
                menu("3메뉴", new BigDecimal("8000"), 5, false));
        serviceAt(MORNING_IN_SEOUL).board("store-01", null);
        assertThat(queue.calls).isEqualTo(1);

        // 메뉴가 없으면 대기열을 물어볼 이유도 없습니다.
        repository.rows = List.of();
        queue.calls = 0;
        serviceAt(MORNING_IN_SEOUL).board("store-01", null);
        assertThat(queue.calls).isZero();
    }

    private static final class RecordingRepository implements MenuRepository {
        private LocalDate askedDate;
        private String askedStore;
        private List<Menu> rows = new ArrayList<>();

        @Override
        public List<Menu> findAll(String storeId, LocalDate on) {
            this.askedStore = storeId;
            this.askedDate = on;
            return rows;
        }

        @Override
        public Optional<Menu> find(String storeId, String menuCode, LocalDate on) {
            this.askedStore = storeId;
            this.askedDate = on;
            return rows.stream().filter(m -> m.menuCode().equals(menuCode)).findFirst();
        }
    }

    private static final class FakeQueue implements OrderQueue {
        private OptionalInt cooking = OptionalInt.of(0);
        private int calls;

        @Override
        public OptionalInt cookingAt(String storeId) {
            calls++;
            return cooking;
        }
    }
}
