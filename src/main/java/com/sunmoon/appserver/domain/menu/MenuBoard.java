package com.sunmoon.appserver.domain.menu;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 손님 화면이 받는 메뉴판 한 장 — 메뉴들과, 그 판이 어느 매장·어느 날의
 * 것인지.
 *
 * <p>메뉴 목록만 주지 않는 이유: 결품과 예상시간은 <b>매장과 날짜에
 * 딸린</b> 값이라, 화면이 무엇을 보고 있는지 같이 말해주지 않으면
 * 캐시된 어제 화면과 구분이 안 됩니다.
 */
public record MenuBoard(String storeId, LocalDate businessDate, List<Item> menus) {

    /**
     * {@code estimatedMinutes}는 <b>기준 조리시간 x (대기열 + 1)</b> 입니다.
     *
     * <p>+1이 자기 주문입니다. 대기열이 0이어도 조리시간은 걸리는데, 그냥
     * 곱하면 "0분"이 나옵니다 — 그건 즉시 나온다는 거짓말입니다.
     *
     * <p>null은 모른다는 뜻이고 세 가지 경우입니다: BO가 기준 조리시간을
     * 안 정했거나, OMS가 안 보여서 대기열을 못 읽었거나, 결품이라 애초에
     * 기다릴 일이 없거나. 셋 다 화면에서는 "표시하지 않음"으로 같습니다 —
     * 모르는 값을 0으로 채우는 것보다 안 보여주는 편이 낫습니다.
     */
    public record Item(
            String menuCode,
            String name,
            String imageUrl,
            String description,
            String kdsDeviceId,
            BigDecimal price,
            boolean soldOut,
            boolean orderable,
            Integer cookMinutes,
            Integer estimatedMinutes) {
    }
}
