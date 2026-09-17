package com.sunmoon.appserver.infrastructure.persistence;

import com.sunmoon.appserver.domain.order.AppOrder;
import com.sunmoon.appserver.domain.order.AppOrderRepository;
import com.sunmoon.appserver.domain.order.AppOrderStatus;
import com.sunmoon.appserver.domain.order.OrderChannel;
import com.sunmoon.appserver.domain.order.OrderType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * APP 주문 원장. 이 서비스가 <b>쓰는</b> 유일한 저장소입니다 — 같은
 * 데이터베이스에 있는 {@code v_bo_*} 뷰들은 BO를 읽기만 합니다.
 */
@Repository
public class JdbcAppOrderRepository implements AppOrderRepository {

    /**
     * Header 는 우리 것, map 은 OMS가 준 것. LEFT JOIN 인 이유는 OMS로
     * 넘기기 전(또는 거절당한) 주문에도 Header 가 있기 때문입니다 —
     * INNER JOIN 이면 그런 주문이 조회에서 조용히 사라지고, 그게 바로
     * 재처리해야 할 주문입니다.
     */
    private static final String SELECT_HEADER = """
            SELECT h.id, h.order_no, h.store_id, h.business_date, h.order_channel, h.order_type,
                   h.customer_id, h.floor, h.table_no, h.qr_code, h.total_amount, h.status,
                   h.placed_at, h.updated_at,
                   m.oms_order_no, m.pickup_no, m.pickup_pin
              FROM app_order_header h
              LEFT JOIN app_oms_order_map m ON m.app_order_id = h.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAppOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * {@code ON CONFLICT DO NOTHING} 이라 같은 요청이 두 번 와도 행이
     * 하나입니다. 돌아오는 것이 비어 있으면 <b>이미 처리한 요청</b>이고,
     * 그때는 새로 만들지 말고 먼저 만든 답을 돌려줘야 합니다.
     */
    @Override
    public Optional<Long> logRequest(String requestId, String payloadJson) {
        List<Long> ids = jdbcTemplate.query("""
                INSERT INTO app_order_log (request_id, payload, status)
                VALUES (?, ?::jsonb, 'RECEIVED')
                ON CONFLICT (request_id) DO NOTHING
                RETURNING id
                """, (rs, n) -> rs.getLong(1), requestId, payloadJson);
        return ids.stream().findFirst();
    }

    @Override
    public Optional<AppOrder> findByRequestId(String requestId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT app_order_id FROM app_order_log WHERE request_id = ? AND app_order_id IS NOT NULL",
                (rs, n) -> rs.getLong(1), requestId);
        return ids.stream().findFirst().flatMap(this::find);
    }

    /**
     * UPSERT 한 문장이 증가와 읽기를 함께 합니다. 읽고-더하고-쓰는 세
     * 걸음이면 그 사이에 다른 주문이 끼어들어 두 손님이 같은 번호를
     * 받습니다.
     */
    @Override
    public int nextSeq(String storeId, LocalDate businessDate) {
        Integer seq = jdbcTemplate.queryForObject("""
                INSERT INTO app_order_seq (store_id, business_date, last_seq)
                VALUES (?, ?, 1)
                ON CONFLICT (store_id, business_date)
                DO UPDATE SET last_seq = app_order_seq.last_seq + 1
                RETURNING last_seq
                """, Integer.class, storeId, Date.valueOf(businessDate));
        return seq == null ? 1 : seq;
    }

    /**
     * Header 와 Detail 을 한 트랜잭션으로 씁니다. 반쪽만 남은 주문은
     * 원장이 아니라 쓰레기입니다 — 금액은 있는데 무엇을 시켰는지 모르는
     * 행이 남으면, 그걸 지울지 살릴지 아무도 판단할 수 없습니다.
     */
    @Override
    @Transactional
    public AppOrder save(AppOrder order) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_order_header
                      (order_no, store_id, business_date, order_channel, order_type, customer_id,
                       floor, table_no, qr_code, total_amount, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, order.orderNo());
            ps.setString(2, order.storeId());
            ps.setDate(3, Date.valueOf(order.businessDate()));
            ps.setString(4, order.channel().name());
            ps.setString(5, order.type().name());
            ps.setString(6, order.customerId());
            if (order.floor() == null) {
                ps.setNull(7, java.sql.Types.INTEGER);
            } else {
                ps.setInt(7, order.floor());
            }
            ps.setString(8, order.tableNo());
            ps.setString(9, order.qrCode());
            ps.setBigDecimal(10, order.totalAmount());
            ps.setString(11, order.status().name());
            return ps;
        }, keys);

        Number key = (Number) keys.getKeys().get("id");
        long id = key.longValue();

        for (AppOrder.Line line : order.lines()) {
            jdbcTemplate.update("""
                    INSERT INTO app_order_detail
                      (app_order_id, detail_seq, menu_code, menu_name, quantity, unit_price,
                       detail_amount, kds_device_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, line.detailSeq(), line.menuCode(), line.menuName(), line.quantity(),
                    line.unitPrice(), line.detailAmount(), line.kdsDeviceId());
        }
        insertHistory(id, order.status(), "APP");

        return find(id).orElseThrow(() -> new IllegalStateException("저장한 주문을 다시 읽지 못했습니다: " + id));
    }

    @Override
    @Transactional
    public void changeStatus(long appOrderId, AppOrderStatus status, String source) {
        jdbcTemplate.update(
                "UPDATE app_order_header SET status = ?, updated_at = now() WHERE id = ?",
                status.name(), appOrderId);
        insertHistory(appOrderId, status, source);
    }

    private void insertHistory(long appOrderId, AppOrderStatus status, String source) {
        jdbcTemplate.update(
                "INSERT INTO app_order_status_hist (app_order_id, status, source) VALUES (?, ?, ?)",
                appOrderId, status.name(), source);
    }

    @Override
    public void completeLog(long logId, Long appOrderId, String status, String error) {
        jdbcTemplate.update(
                "UPDATE app_order_log SET app_order_id = ?, status = ?, error = ? WHERE id = ?",
                appOrderId, status, error, logId);
    }

    /**
     * 같은 OMS 주문번호가 두 번 오면 유일 제약이 막습니다. 재시도로 두
     * 번째가 오는 것은 흔한 일이라, 이미 같은 값이면 실패가 아닙니다 —
     * 다른 값이면 그때는 진짜 사고이므로 던집니다.
     */
    @Override
    public void mapToOms(long appOrderId, String omsOrderNo, String pickupNo, String pickupPin) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO app_oms_order_map (app_order_id, oms_order_no, pickup_no, pickup_pin)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (app_order_id) DO NOTHING
                    """, appOrderId, omsOrderNo, pickupNo, pickupPin);
        } catch (DuplicateKeyException alreadyMappedElsewhere) {
            throw new IllegalStateException(
                    "OMS 주문번호 " + omsOrderNo + " 가 다른 APP 주문에 이미 붙어 있습니다", alreadyMappedElsewhere);
        }
    }

    /**
     * 결제 한 건.
     *
     * <p>{@code ON CONFLICT DO NOTHING} 인 것은 멱등성 때문입니다.
     * 같은 요청이 재시도로 두 번 들어오면 주문은 하나인데(§4.4.6)
     * 결제가 둘이 될 수 있고, 그러면 손님이 두 번 낸 것으로 보입니다.
     */
    @Override
    public void savePayment(long appOrderId, String method, java.math.BigDecimal amount,
                            String status, String approvalRef) {
        jdbcTemplate.update("""
                INSERT INTO app_order_payment
                  (app_order_id, payment_seq, payment_type, payment_amount,
                   payment_status, approval_ref, paid_at)
                VALUES (?, 1, ?, ?, ?, ?, now())
                ON CONFLICT (app_order_id, payment_seq) DO NOTHING
                """, appOrderId, method, amount, status, approvalRef);
    }

    @Override
    public java.util.Optional<String> unrefundedApprovalOf(long appOrderId) {
        return jdbcTemplate.query("""
                SELECT approval_ref FROM app_order_payment
                 WHERE app_order_id = ? AND payment_status = 'APPROVED'
                """, (rs, n) -> rs.getString("approval_ref"), appOrderId)
                .stream().findFirst();
    }

    /**
     * 돌려줬습니다.
     *
     * <p>승인된 건만 바꿉니다 — 애초에 거절된 결제를 환불로 만들면
     * 갚은 적 없는 돈이 장부에 생깁니다.
     *
     * <p>승인번호를 <b>지우지 않습니다</b>. 환불번호를 뒤에 붙입니다 —
     * "무엇을 돌려준 것인지"가 없으면 정산에서 짝을 못 이룹니다.
     */
    @Override
    public void markRefunded(long appOrderId, String refundNo) {
        jdbcTemplate.update("""
                UPDATE app_order_payment
                   SET payment_status = 'REFUNDED',
                       approval_ref = approval_ref || ' / ' || ?
                 WHERE app_order_id = ? AND payment_status = 'APPROVED'
                """, refundNo, appOrderId);
    }

    /**
     * 조건을 SQL로 쌓습니다. 전부 받아서 자바에서 거르면, 매장이 늘어난
     * 날 한 매장을 보려고 전 매장의 주문을 실어 나르게 됩니다.
     */
    @Override
    public List<AppOrder> findByDay(String storeId, LocalDate businessDate, OrderChannel channel) {
        StringBuilder sql = new StringBuilder(SELECT_HEADER).append(" WHERE h.business_date = ?");
        List<Object> args = new java.util.ArrayList<>();
        args.add(Date.valueOf(businessDate));
        if (storeId != null && !storeId.isBlank()) {
            sql.append(" AND h.store_id = ?");
            args.add(storeId);
        }
        if (channel != null) {
            sql.append(" AND h.order_channel = ?");
            args.add(channel.name());
        }
        sql.append(" ORDER BY h.id DESC");
        return jdbcTemplate.query(sql.toString(), headerMapper(), args.toArray());
    }

    @Override
    public Optional<AppOrder> find(long id) {
        List<AppOrder> headers = jdbcTemplate.query(
                SELECT_HEADER + " WHERE h.id = ?", headerMapper(), id);
        return headers.stream().findFirst();
    }

    private RowMapper<AppOrder> headerMapper() {
        return (rs, rowNum) -> {
            long id = rs.getLong("id");
            List<AppOrder.Line> lines = jdbcTemplate.query("""
                    SELECT detail_seq, menu_code, menu_name, quantity, unit_price, detail_amount,
                           kds_device_id
                      FROM app_order_detail WHERE app_order_id = ? ORDER BY detail_seq
                    """, (r, n) -> new AppOrder.Line(
                    r.getInt("detail_seq"), r.getString("menu_code"), r.getString("menu_name"),
                    r.getInt("quantity"), r.getBigDecimal("unit_price"), r.getBigDecimal("detail_amount"),
                    r.getString("kds_device_id")),
                    id);

            return new AppOrder(
                    id,
                    rs.getString("order_no"),
                    rs.getString("store_id"),
                    rs.getObject("business_date", LocalDate.class),
                    OrderChannel.valueOf(rs.getString("order_channel")),
                    OrderType.valueOf(rs.getString("order_type")),
                    rs.getString("customer_id"),
                    nullableInt(rs, "floor"),
                    rs.getString("table_no"),
                    rs.getString("qr_code"),
                    rs.getBigDecimal("total_amount"),
                    AppOrderStatus.valueOf(rs.getString("status")),
                    lines,
                    rs.getString("oms_order_no"),
                    rs.getString("pickup_no"),
                    rs.getString("pickup_pin"),
                    instant(rs.getTimestamp("placed_at")),
                    instant(rs.getTimestamp("updated_at")));
        };
    }

    /** null과 0을 구분해야 합니다 — getInt는 null을 0으로 돌려줍니다. */
    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static java.time.Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
