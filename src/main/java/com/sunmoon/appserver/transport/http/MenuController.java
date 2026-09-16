package com.sunmoon.appserver.transport.http;

import com.sunmoon.appserver.domain.menu.Menu;
import com.sunmoon.appserver.domain.menu.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 메뉴 확인. 조회 전용입니다 — 메뉴는 BO가 소유하고 이 서버는 읽기만 합니다.
 *
 * <p>아직 매장별 메뉴가 아닙니다. BO의 메뉴 마스터에 매장 구분이 없어서
 * 모든 매장이 같은 목록을 씁니다(BO README의 "매장별 메뉴" 항목). 여기서
 * 지어내면 나중에 진짜 매장별 메뉴가 생길 때 두 군데를 고치게 됩니다.
 */
@RestController
@RequestMapping("/menus")
@Tag(name = "Menus", description = "메뉴 확인 — BO 마스터를 읽어옵니다")
public class MenuController {

    private final MenuService service;

    public MenuController(MenuService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "메뉴 목록",
            description = "date를 비우면 매장 달력(Asia/Seoul) 기준 오늘입니다. "
                    + "가격은 그 날짜에 유효한 이력 행에서 옵니다 — 덮는 행이 없으면 "
                    + "price가 null이고, 0원이 아니라 그날은 팔 수 없다는 뜻입니다.")
    public List<Menu> menus(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.menus(date);
    }
}
