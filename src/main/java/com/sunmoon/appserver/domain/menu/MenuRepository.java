package com.sunmoon.appserver.domain.menu;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * BO 메뉴 마스터를 읽습니다. <b>읽기만 합니다</b> — 쓰기 메서드가 없는 것은
 * 실수가 아니라 이 서버의 역할이고, 데이터베이스 권한(bo_reader)이 같은 것을
 * 한 번 더 강제합니다.
 *
 * <p>매장을 받는 이유는 메뉴가 매장마다 달라서가 아닙니다 (아직 안 그렇습니다).
 * <b>결품</b>이 매장마다 다르기 때문입니다 — 같은 메뉴가 1호점엔 있고
 * 2호점엔 없습니다.
 */
public interface MenuRepository {

    /** 그 매장의 그 날짜 메뉴판. 가격과 결품이 붙습니다. 사용 중지된 메뉴는 빠집니다. */
    List<Menu> findAll(String storeId, LocalDate on);

    Optional<Menu> find(String storeId, String menuCode, LocalDate on);
}
