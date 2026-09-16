package com.sunmoon.appserver.infrastructure.persistence;

import com.sunmoon.appserver.domain.menu.Menu;
import com.sunmoon.appserver.domain.menu.MenuRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code v_bo_menu} / {@code v_bo_menu_price} / {@code v_bo_menu_sold_out}
 * 는 이 데이터베이스의 평범한 뷰처럼 보이지만, 실제로는 {@code bo_service}
 * 를 가리키는 postgres_fdw 외부 테이블 위에 얹혀 있습니다 — 이 클래스는
 * 그 사실을 몰라도 됩니다. 뷰를 만드는 것은
 * {@code Debian-Setting/server/db/app-server-fdw.sql} 입니다.
 *
 * <p>가격을 LEFT JOIN 으로 붙이는 것이 핵심입니다. INNER JOIN 이면 그 날짜에
 * 가격이 없는 메뉴가 <b>목록에서 조용히 사라집니다</b> — 그러면 "메뉴가 왜
 * 안 보이지"를 디버깅하게 됩니다. 나오되 주문할 수 없는 편이 낫습니다.
 *
 * <p>결품도 같은 이유로 LEFT JOIN 입니다. 결품인 메뉴를 목록에서 빼면
 * 손님은 그 메뉴가 원래 없는 줄 알고, 매장에는 "왜 안 파냐"가 아니라
 * "메뉴가 왜 없어졌냐"는 전화가 옵니다. 보여주되 못 고르게 합니다.
 */
@Repository
public class JdbcMenuRepository implements MenuRepository {

    private static final String SELECT = """
            SELECT m.menu_code, m.name, m.image_url, m.description, m.kds_device_id,
                   m.cook_minutes, p.price,
                   (s.menu_code IS NOT NULL) AS sold_out
              FROM v_bo_menu m
              LEFT JOIN v_bo_menu_price p
                ON p.menu_code = m.menu_code
               AND p.start_date <= ?
               AND (p.end_date IS NULL OR p.end_date >= ?)
              LEFT JOIN v_bo_menu_sold_out s
                ON s.menu_code = m.menu_code
               AND s.store_id = ?
               AND s.business_date = ?
             WHERE m.active = TRUE
            """;

    private static final RowMapper<Menu> MAPPER = (rs, rowNum) -> new Menu(
            rs.getString("menu_code"),
            rs.getString("name"),
            rs.getString("image_url"),
            rs.getString("description"),
            rs.getString("kds_device_id"),
            rs.getBigDecimal("price"),
            nullableInt(rs, "cook_minutes"),
            rs.getBoolean("sold_out"));

    /** null과 0을 구분해야 합니다 — getInt는 null을 0으로 돌려줍니다. */
    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private final JdbcTemplate jdbcTemplate;

    public JdbcMenuRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Menu> findAll(String storeId, LocalDate on) {
        Date d = Date.valueOf(on);
        return jdbcTemplate.query(SELECT + " ORDER BY m.menu_code", MAPPER, d, d, storeId, d);
    }

    @Override
    public Optional<Menu> find(String storeId, String menuCode, LocalDate on) {
        Date d = Date.valueOf(on);
        return jdbcTemplate.query(SELECT + " AND m.menu_code = ?", MAPPER, d, d, storeId, d, menuCode)
                .stream().findFirst();
    }
}
