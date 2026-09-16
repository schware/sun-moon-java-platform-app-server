package com.sunmoon.appserver.domain.menu;

import java.math.BigDecimal;

/**
 * 손님에게 보여줄 메뉴 한 줄.
 *
 * <p>BO가 소유한 기준 정보를 읽어 온 것입니다 — 이 서버는 메뉴를 만들지도
 * 고치지도 않습니다. 읽는 경로는 postgres_fdw 뷰이고, 서비스가 아니라
 * 데이터베이스를 읽기 때문에 BO 컨테이너가 내려가 있어도 메뉴는 나옵니다
 * (umbrella {@code docs/adr/0008}).
 *
 * <p>{@code price}는 조회한 <b>날짜에 유효한</b> 가격입니다. BO의 메뉴 가격은
 * 단일 컬럼이 아니라 효과-일자 이력이라, "오늘 가격"이라는 값은 없고 날짜를
 * 주고 물어봐야 합니다. 그 날짜를 덮는 가격 행이 없으면 null이고, 그건
 * 값이 0원이라는 뜻이 아니라 <b>팔 수 없다</b>는 뜻입니다.
 *
 * <p>{@code kdsDeviceId}는 이 메뉴가 어느 주방 화면으로 갈지입니다. 손님에게
 * 보여줄 값은 아니지만, 주문을 넣을 때 함께 실어야 해서 여기 실려 옵니다.
 *
 * <p>{@code soldOut}은 <b>오늘 이 매장에</b> 없다는 뜻입니다. 메뉴를 내린
 * 것({@code active=false})과 다릅니다 — 저쪽은 애초에 목록에 안 나오고,
 * 이쪽은 나오되 못 고릅니다. 손님에게는 "없다"가 보여야지 메뉴가 통째로
 * 사라지면 매장에 전화가 옵니다.
 *
 * <p>{@code cookMinutes}는 BO가 정한 <b>기준</b> 조리시간입니다. 실측이
 * 아니라서 혼자서는 아무 뜻이 없고, 대기열 길이와 곱해야 예상시간이
 * 됩니다 — 그 곱셈은 이 record가 아니라 {@link MenuService}가 합니다.
 */
public record Menu(
        String menuCode,
        String name,
        String imageUrl,
        String description,
        String kdsDeviceId,
        BigDecimal price,
        Integer cookMinutes,
        boolean soldOut) {

    /**
     * 가격이 정해지지 않은 날의 메뉴는 주문할 수 없고, 오늘 없는 메뉴도
     * 주문할 수 없습니다. 이유가 둘이지만 손님에게는 결과가 하나입니다.
     */
    public boolean orderable() {
        return price != null && !soldOut;
    }
}
