package com.sunmoon.appserver.domain.entry;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게이트가 하는 일은 통과/거절이 아니라 <b>왜 거절인지</b>를 구분하는
 * 것입니다. 이유마다 손님이 할 수 있는 일이 다릅니다 — 마감이면 기다리면
 * 되고, 잘못된 QR이면 직원을 불러야 하고, 못 물어봤으면 다시 시도하면
 * 됩니다. 그래서 각 갈래를 따로 잡습니다.
 */
class TableEntryServiceTest {

    private final StubTables tables = new StubTables();
    private final StubBusinessDays days = new StubBusinessDays();
    private final TableEntryService service = new TableEntryService(tables, days);

    private void tableExists(String code, boolean active) {
        tables.byCode.put(code, new StoreTable(1L, "store-01", 1, "3", null, active));
        tables.names.put("store-01", "1매장");
    }

    @Test
    void passesAndCarriesTheBusinessDate() {
        tableExists("ABC", true);
        days.status = new BusinessDayStatus(true, "2026-09-16");

        TableEntryService.Result result = service.enter("ABC");

        assertThat(result.allowed()).isTrue();
        assertThat(result.entry().storeName()).isEqualTo("1매장");
        assertThat(result.entry().tableNo()).isEqualTo("3");
        // 주문이 이 날짜로 기록되므로 손님 화면에도 같은 값이 보여야
        // 나중에 영수증과 대조가 됩니다.
        assertThat(result.entry().businessDate()).isEqualTo("2026-09-16");
    }

    @Test
    void refusesAnUnknownCode() {
        days.status = new BusinessDayStatus(true, "2026-09-16");

        assertThat(service.enter("NOPE").refusal()).isEqualTo(EntryRefusal.UNKNOWN_CODE);
    }

    @Test
    void refusesADisabledTable() {
        tableExists("ABC", false);
        days.status = new BusinessDayStatus(true, "2026-09-16");

        assertThat(service.enter("ABC").refusal()).isEqualTo(EntryRefusal.TABLE_INACTIVE);
    }

    @Test
    void refusesWhenTheTablePointsAtAStoreThatIsGone() {
        tables.byCode.put("ABC", new StoreTable(1L, "store-99", 1, "3", null, true));
        days.status = new BusinessDayStatus(true, "2026-09-16");

        assertThat(service.enter("ABC").refusal()).isEqualTo(EntryRefusal.UNKNOWN_STORE);
    }

    @Test
    void refusesAClosedStore() {
        tableExists("ABC", true);
        days.status = BusinessDayStatus.closed();

        assertThat(service.enter("ABC").refusal()).isEqualTo(EntryRefusal.STORE_CLOSED);
    }

    @Test
    void tellsUnreachableApartFromClosed() {
        tableExists("ABC", true);
        days.unreachable = true;

        // 이 둘을 합치면, Order를 재배포하는 30초 동안 모든 매장이 마감된
        // 것처럼 보이고 손님은 그냥 나갑니다.
        assertThat(service.enter("ABC").refusal()).isEqualTo(EntryRefusal.STORE_UNREACHABLE);
    }

    @Test
    void doesNotAskAboutBusinessDaysWhenTheCodeIsUnknown() {
        days.unreachable = true;

        // 없는 코드인데 Order까지 다녀오면, 장난으로 찍은 QR 하나가
        // 매번 뒤쪽 서비스를 두드립니다.
        assertThat(service.enter("NOPE").refusal()).isEqualTo(EntryRefusal.UNKNOWN_CODE);
        assertThat(days.asked).isFalse();
    }

    private static final class StubTables implements TableDirectory {
        private final Map<String, StoreTable> byCode = new HashMap<>();
        private final Map<String, String> names = new HashMap<>();

        @Override
        public Optional<StoreTable> byQrCode(String qrCode) {
            return Optional.ofNullable(byCode.get(qrCode));
        }

        @Override
        public Optional<String> storeName(String storeId) {
            return Optional.ofNullable(names.get(storeId));
        }
    }

    private static final class StubBusinessDays implements BusinessDays {
        private BusinessDayStatus status = BusinessDayStatus.closed();
        private boolean unreachable;
        private boolean asked;

        @Override
        public BusinessDayStatus statusOf(String storeId) {
            asked = true;
            if (unreachable) {
                throw new StoreUnreachableException(storeId, new RuntimeException("stub"));
            }
            return status;
        }
    }
}
