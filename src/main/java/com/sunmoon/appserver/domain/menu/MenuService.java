package com.sunmoon.appserver.domain.menu;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 메뉴 확인 — 손님 화면과 QR 진입이 둘 다 쓰는 첫 기능.
 *
 * <p>날짜를 안 주면 <b>매장의 달력</b>으로 오늘입니다. 서버가 UTC로 도는
 * 호스트라, 한국 시간 아침 8시에 물어보면 어제 가격이 나오는 일이 생깁니다 —
 * Order의 영업일자가 같은 이유로 Asia/Seoul을 쓰고, 여기도 같아야
 * 메뉴 가격과 매출이 서로 다른 날을 가리키지 않습니다.
 */
@Service
public class MenuService {

    /** 가게의 달력. 이 호스트는 UTC로 돕니다. */
    static final ZoneId STORE_ZONE = ZoneId.of("Asia/Seoul");

    private final MenuRepository repository;
    private final Clock clock;

    public MenuService(MenuRepository repository) {
        this(repository, Clock.system(STORE_ZONE));
    }

    /** 테스트가 "오늘"을 정할 수 있게. */
    MenuService(MenuRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(STORE_ZONE));
    }

    public List<Menu> menus(LocalDate on) {
        return repository.findAll(on == null ? today() : on);
    }
}
