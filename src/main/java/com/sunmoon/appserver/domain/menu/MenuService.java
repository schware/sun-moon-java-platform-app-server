package com.sunmoon.appserver.domain.menu;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalInt;

/**
 * 메뉴 확인 — 손님 화면과 QR 진입이 둘 다 쓰는 첫 기능.
 *
 * <p>날짜를 안 주면 <b>매장의 달력</b>으로 오늘입니다. 서버가 UTC로 도는
 * 호스트라, 한국 시간 아침 8시에 물어보면 어제 가격이 나오는 일이 생깁니다 —
 * Order의 영업일자가 같은 이유로 Asia/Seoul을 쓰고, 여기도 같아야
 * 메뉴 가격과 매출이 서로 다른 날을 가리키지 않습니다.
 *
 * <p>결품과 예상시간이 붙으면서 <b>매장</b>이 필요해졌습니다. 메뉴 자체는
 * 아직 전 매장 공통이지만, "오늘 여기 없다"와 "여기 얼마나 밀렸다"는
 * 매장마다 다릅니다.
 */
@Service
public class MenuService {

    /** 가게의 달력. 이 호스트는 UTC로 돕니다. */
    static final ZoneId STORE_ZONE = ZoneId.of("Asia/Seoul");

    private final MenuRepository repository;
    private final OrderQueue queue;
    private final Clock clock;

    // 생성자가 둘이면 Spring이 어느 것을 쓸지 고르지 못하고, 실패는 컴파일이
    // 아니라 **기동 시점**에 납니다. Order의 BusinessDayService가 정확히
    // 이것으로 한 번 죽었고, 이 서비스도 첫 배포에서 같은 이유로 죽었습니다.
    // 이 어노테이션은 장식이 아니라 동작에 필요한 것입니다.
    @Autowired
    public MenuService(MenuRepository repository, OrderQueue queue) {
        this(repository, queue, Clock.system(STORE_ZONE));
    }

    /** 테스트가 "오늘"을 정할 수 있게. */
    MenuService(MenuRepository repository, OrderQueue queue, Clock clock) {
        this.repository = repository;
        this.queue = queue;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(STORE_ZONE));
    }

    public MenuBoard board(String storeId, LocalDate on) {
        LocalDate date = on == null ? today() : on;
        List<Menu> menus = repository.findAll(storeId, date);

        // 대기열은 한 번만 물어봅니다 — 메뉴마다 물으면 메뉴 열 개짜리
        // 화면 한 장에 OMS 호출이 열 번 갑니다.
        OptionalInt cooking = menus.isEmpty() ? OptionalInt.empty() : queue.cookingAt(storeId);

        return new MenuBoard(storeId, date, menus.stream().map(m -> item(m, cooking)).toList());
    }

    private static MenuBoard.Item item(Menu menu, OptionalInt cooking) {
        return new MenuBoard.Item(
                menu.menuCode(), menu.name(), menu.imageUrl(), menu.description(), menu.kdsDeviceId(),
                menu.price(), menu.soldOut(), menu.orderable(), menu.cookMinutes(),
                estimatedMinutes(menu, cooking));
    }

    /**
     * 기준 조리시간 x (대기열 + 1). 셋 중 하나라도 모르면 null입니다 —
     * 자세한 이유는 {@link MenuBoard.Item} 에 적어 두었습니다.
     */
    static Integer estimatedMinutes(Menu menu, OptionalInt cooking) {
        if (menu.soldOut() || menu.cookMinutes() == null || cooking.isEmpty()) {
            return null;
        }
        return menu.cookMinutes() * (cooking.getAsInt() + 1);
    }
}
