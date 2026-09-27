package com.sunmoon.appserver.infrastructure.persistence;

import com.sunmoon.appserver.domain.payment.AcceptedMethods;
import com.sunmoon.appserver.domain.payment.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * BO 기준정보의 결제수단을 뷰로 읽습니다 ({@code v_bo_payment_method},
 * umbrella docs/adr/0008).
 *
 * <p>주문마다 한 번 읽습니다. 캐시를 두지 않은 이유: 행이 몇 개뿐이고
 * 같은 데이터베이스 안의 뷰라 싸고, 무엇보다 <b>운영자가 끈 것이 바로
 * 먹어야</b> 합니다 — "껐는데 1분 동안 결제가 됐다" 는 설명하기 어렵습니다.
 */
@Repository
public class JdbcAcceptedMethods implements AcceptedMethods {

    private static final Logger log = LoggerFactory.getLogger(JdbcAcceptedMethods.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAcceptedMethods(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Acceptance of(PaymentMethod method) {
        List<Boolean> active;
        try {
            active = jdbcTemplate.queryForList(
                    "SELECT active FROM v_bo_payment_method WHERE pay_method_code = ?",
                    Boolean.class, method.name());
        } catch (DataAccessException unreadable) {
            // 뷰가 없거나(fdw 스크립트를 안 돌린 서버) BO 쪽을 못 읽는 경우.
            // 모르는 채로 막으면 기준정보를 읽는 길 하나가 장사 전체를 멈춥니다.
            log.warn("결제수단 목록을 읽지 못했습니다 — {} 을 받고 진행합니다. "
                    + "v_bo_payment_method 뷰가 있는지 확인하세요", method, unreadable);
            return Acceptance.UNKNOWN;
        }
        // 목록에 없는 것은 "받지 않는다" 입니다. 이 표의 뜻이 "브랜드가
        // 받기로 한 것" 이라, 빈 칸은 받기로 하지 않은 것입니다.
        if (active.isEmpty() || !Boolean.TRUE.equals(active.get(0))) {
            return Acceptance.DISABLED;
        }
        return Acceptance.ACCEPTED;
    }
}
