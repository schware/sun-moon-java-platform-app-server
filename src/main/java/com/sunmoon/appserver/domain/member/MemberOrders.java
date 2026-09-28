package com.sunmoon.appserver.domain.member;

import com.sunmoon.appserver.domain.order.OmsGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 회원의 주문 이력 (customer-app.md §3.4) — 토큰이 있을 때만 봅니다.
 *
 * <p>상태는 원장의 것(SENT 등)이 아니라 <b>매장이 어디까지 했나</b>(OMS)입니다 — 손님이 궁금한
 * 것이 그것입니다. ▶ 30건을 OMS 에 하나씩 묻지 않고, <b>오늘 주문만</b> OMS 에 물어 채웁니다.
 * 지난 주문은 원장 상태 그대로입니다.
 */
@Service
public class MemberOrders {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    public static final int MAX = 50;

    private final JdbcTemplate jdbc;
    private final OmsGateway oms;

    public MemberOrders(JdbcTemplate jdbc, OmsGateway oms) {
        this.jdbc = jdbc;
        this.oms = oms;
    }

    public record Row(String orderNo, String storeId, LocalDate businessDate, String summary,
                      BigDecimal amount, String status, String pickupNo, String claimToken, Instant placedAt) {
    }

    public List<Row> of(String memberNo, int limit) {
        int n = Math.max(1, Math.min(limit, MAX));
        LocalDate today = LocalDate.now(SEOUL);
        List<Object[]> raw = jdbc.query("""
                SELECT h.order_no, h.store_id, h.business_date, h.total_amount, h.status, h.placed_at,
                       m.oms_order_no, m.pickup_no, m.claim_token,
                       (SELECT d.menu_name FROM app_order_detail d WHERE d.app_order_id = h.id
                         ORDER BY d.detail_seq LIMIT 1) AS first_menu,
                       (SELECT count(*) FROM app_order_detail d WHERE d.app_order_id = h.id) AS lines
                  FROM app_order_header h
                  LEFT JOIN app_oms_order_map m ON m.app_order_id = h.id
                 WHERE h.customer_id = ?
                 ORDER BY h.placed_at DESC
                 LIMIT ?
                """, (rs, i) -> new Object[] {
                rs.getString("order_no"), rs.getString("store_id"), rs.getDate("business_date"),
                rs.getBigDecimal("total_amount"), rs.getString("status"), rs.getTimestamp("placed_at"),
                rs.getString("oms_order_no"), rs.getString("pickup_no"), rs.getString("claim_token"),
                rs.getString("first_menu"), rs.getInt("lines")}, memberNo, n);
        return raw.stream().map(r -> {
            LocalDate day = ((Date) r[2]).toLocalDate();
            String status = (String) r[4];
            String omsNo = (String) r[6];
            if (omsNo != null && day.equals(today)) {
                status = oms.statusOf(omsNo).orElse(status);
            }
            String first = (String) r[9];
            int lines = (Integer) r[10];
            String summary = first == null ? null : lines > 1 ? first + " 외 " + (lines - 1) + "건" : first;
            return new Row((String) r[0], (String) r[1], day, summary, (BigDecimal) r[3], status,
                    (String) r[7], (String) r[8], ((java.sql.Timestamp) r[5]).toInstant());
        }).toList();
    }
}
