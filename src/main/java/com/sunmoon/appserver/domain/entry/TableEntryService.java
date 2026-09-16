package com.sunmoon.appserver.domain.entry;

import org.springframework.stereotype.Service;

/**
 * QR 게이트 — 메뉴를 보여주기 <b>전에</b> 통과해야 하는 네 가지 확인.
 *
 * <pre>
 *   QR 코드가 유효한가        → UNKNOWN_CODE
 *   그 테이블이 살아있는가     → TABLE_INACTIVE
 *   매장이 존재하는가          → UNKNOWN_STORE
 *   매장이 영업 중인가         → STORE_CLOSED
 * </pre>
 *
 * <p>연결 handshake는 없습니다. 단말이 소켓을 열 때 신원을 등록하는 절차와
 * 달리, 여기는 쥐고 있는 연결이 없으므로 평범한 요청 한 번입니다 — 실패하면
 * 다시 찍으면 됩니다.
 *
 * <p>확인 순서에 의미가 있습니다. 앞의 셋은 <b>데이터가 어긋난 상태</b>라
 * 손님이 할 수 있는 일이 없고 직원을 불러야 합니다. 마지막 하나만 기다리면
 * 되는 경우입니다. 그래서 이유를 뭉뚱그리지 않습니다.
 */
@Service
public class TableEntryService {

    /** 통과했으면 {@code entry}, 아니면 {@code refusal} — 둘 중 하나만 채워집니다. */
    public record Result(TableEntry entry, EntryRefusal refusal) {

        static Result of(TableEntry entry) {
            return new Result(entry, null);
        }

        static Result refused(EntryRefusal refusal) {
            return new Result(null, refusal);
        }

        public boolean allowed() {
            return entry != null;
        }
    }

    private final TableDirectory tables;
    private final BusinessDays businessDays;

    public TableEntryService(TableDirectory tables, BusinessDays businessDays) {
        this.tables = tables;
        this.businessDays = businessDays;
    }

    public Result enter(String qrCode) {
        StoreTable table = tables.byQrCode(qrCode).orElse(null);
        if (table == null) {
            return Result.refused(EntryRefusal.UNKNOWN_CODE);
        }
        if (!table.active()) {
            return Result.refused(EntryRefusal.TABLE_INACTIVE);
        }

        String storeName = tables.storeName(table.storeId()).orElse(null);
        if (storeName == null) {
            // 테이블은 있는데 그 매장이 기준 정보에 없습니다 — 지워진
            // 매장의 테이블이 남았거나, 매장 코드를 잘못 넣은 것입니다.
            return Result.refused(EntryRefusal.UNKNOWN_STORE);
        }

        BusinessDayStatus status;
        try {
            status = businessDays.statusOf(table.storeId());
        } catch (BusinessDays.StoreUnreachableException e) {
            // 못 물어본 것과 닫힌 것은 다릅니다. 닫힘으로 뭉개면 Order를
            // 재배포하는 동안 모든 매장이 마감된 것처럼 보입니다.
            return Result.refused(EntryRefusal.STORE_UNREACHABLE);
        }
        if (!status.open()) {
            return Result.refused(EntryRefusal.STORE_CLOSED);
        }

        return Result.of(new TableEntry(
                table.storeId(), storeName, table.floor(), table.tableNo(),
                table.label(), status.businessDate()));
    }
}
