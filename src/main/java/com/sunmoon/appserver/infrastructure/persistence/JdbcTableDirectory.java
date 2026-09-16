package com.sunmoon.appserver.infrastructure.persistence;

import com.sunmoon.appserver.domain.entry.StoreTable;
import com.sunmoon.appserver.domain.entry.TableDirectory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@code v_bo_table} / {@code v_bo_store} 는 평범한 로컬 뷰처럼 보이지만
 * {@code bo_service} 를 가리키는 postgres_fdw 외부 테이블 위에 있습니다 —
 * 이 클래스는 그 사실을 몰라도 됩니다 (umbrella {@code docs/adr/0008}).
 */
@Repository
public class JdbcTableDirectory implements TableDirectory {

    private static final RowMapper<StoreTable> MAPPER = (rs, rowNum) -> new StoreTable(
            rs.getLong("id"),
            rs.getString("store_id"),
            rs.getInt("floor"),
            rs.getString("table_no"),
            rs.getString("display_name"),
            rs.getBoolean("active"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcTableDirectory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StoreTable> byQrCode(String qrCode) {
        return jdbcTemplate.query(
                        "SELECT id, store_id, floor, table_no, display_name, active "
                                + "FROM v_bo_table WHERE qr_code = ?", MAPPER, qrCode)
                .stream().findFirst();
    }

    @Override
    public Optional<String> storeName(String storeId) {
        // 사용 중지된 매장은 없는 것으로 봅니다 — 손님이 거기서 주문할
        // 일은 없고, "있지만 못 쓴다"를 따로 알릴 이유도 없습니다.
        return jdbcTemplate.query(
                        "SELECT name FROM v_bo_store WHERE store_id = ? AND active = TRUE",
                        (rs, rowNum) -> rs.getString("name"), storeId)
                .stream().findFirst();
    }
}
