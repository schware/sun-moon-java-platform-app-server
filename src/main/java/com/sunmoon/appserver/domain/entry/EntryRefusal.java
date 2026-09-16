package com.sunmoon.appserver.domain.entry;

/**
 * 게이트가 손님을 돌려보내는 이유.
 *
 * <p>화면에 보일 문장을 여기 두는 것은, 이유마다 손님이 할 수 있는 일이
 * 다르기 때문입니다 — 마감이면 기다리면 되고, 잘못된 QR이면 직원을 불러야
 * 합니다. "주문할 수 없습니다" 한 줄로 뭉뚱그리면 둘 다 직원을 부릅니다.
 */
public enum EntryRefusal {

    /** 이 코드로 찾아지는 테이블이 없습니다. 지워졌거나, 다른 매장 것이거나, 오타. */
    UNKNOWN_CODE("이 QR로는 주문할 수 없습니다. 직원에게 알려주세요."),

    /** 테이블은 있지만 사용 중지입니다 — 치웠거나 공사 중이거나. */
    TABLE_INACTIVE("이 자리는 지금 주문을 받지 않습니다. 직원에게 알려주세요."),

    /** 테이블이 가리키는 매장이 기준 정보에 없습니다. 데이터가 어긋난 상태. */
    UNKNOWN_STORE("매장 정보를 찾을 수 없습니다. 직원에게 알려주세요."),

    /** 매장이 개점 전이거나 마감했습니다. 손님이 기다리면 되는 유일한 경우. */
    STORE_CLOSED("지금은 주문을 받지 않습니다."),

    /** 영업 여부를 물어볼 수 없었습니다. 닫힌 것과 구분해야 합니다. */
    STORE_UNREACHABLE("잠시 후 다시 시도해주세요.");

    private final String message;

    EntryRefusal(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
