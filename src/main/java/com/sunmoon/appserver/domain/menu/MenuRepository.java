package com.sunmoon.appserver.domain.menu;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * BO 메뉴 마스터를 읽습니다. <b>읽기만 합니다</b> — 쓰기 메서드가 없는 것은
 * 실수가 아니라 이 서버의 역할이고, 데이터베이스 권한(bo_reader)이 같은 것을
 * 한 번 더 강제합니다.
 */
public interface MenuRepository {

    /** 그 날짜에 유효한 가격까지 붙여서. 사용 중지된 메뉴는 빠집니다. */
    List<Menu> findAll(LocalDate on);

    Optional<Menu> find(String menuCode, LocalDate on);
}
