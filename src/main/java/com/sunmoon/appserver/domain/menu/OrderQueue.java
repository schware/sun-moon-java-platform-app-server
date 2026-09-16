package com.sunmoon.appserver.domain.menu;

import java.util.OptionalInt;

/**
 * 주방에 지금 몇 건이 밀려 있는가. OMS만 아는 값입니다.
 *
 * <p>인터페이스로 끊어둔 이유는 테스트 때문만이 아닙니다 — 예상시간은
 * <b>없어도 되는 값</b>이고, OMS가 안 보일 때 메뉴 목록까지 같이 죽으면
 * 안 됩니다. 그래서 돌려주는 것이 {@code OptionalInt} 입니다: "0건"과
 * "모르겠다"를 구분해야 화면이 0분이라고 거짓말하지 않습니다.
 */
public interface OrderQueue {

    OptionalInt cookingAt(String storeId);
}
