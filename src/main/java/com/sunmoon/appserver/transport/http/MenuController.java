package com.sunmoon.appserver.transport.http;

import com.sunmoon.appserver.domain.menu.MenuBoard;
import com.sunmoon.appserver.domain.menu.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 메뉴 확인. 조회 전용입니다 — 메뉴는 BO가 소유하고 이 서버는 읽기만 합니다.
 *
 * <p>메뉴 <b>목록</b>은 아직 매장별이 아닙니다. BO의 메뉴 마스터에 매장
 * 구분이 없어서 모든 매장이 같은 목록을 씁니다(BO README의 "매장별 메뉴"
 * 항목). 여기서 지어내면 나중에 진짜 매장별 메뉴가 생길 때 두 군데를
 * 고치게 됩니다.
 *
 * <p>그래도 {@code storeId}는 필수입니다 — 결품과 예상 대기시간은 지금도
 * 매장마다 다릅니다. 매장을 고르지 않고 볼 수 있는 메뉴판은 손님에게
 * 쓸모가 없습니다.
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
    @Operation(summary = "메뉴판",
            description = "date를 비우면 매장 달력(Asia/Seoul) 기준 오늘입니다. "
                    + "가격은 그 날짜에 유효한 이력 행에서 옵니다 — 덮는 행이 없으면 "
                    + "price가 null이고, 0원이 아니라 그날은 팔 수 없다는 뜻입니다. "
                    + "soldOut은 오늘 이 매장에 없다는 뜻이고(메뉴를 내린 것과 다릅니다), "
                    + "estimatedMinutes는 기준 조리시간 x (대기열 + 1) 입니다 — "
                    + "모르면 null이고 0으로 읽으면 안 됩니다.")
    public MenuBoard menus(
            @RequestParam String storeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.board(storeId, date);
    }
}
