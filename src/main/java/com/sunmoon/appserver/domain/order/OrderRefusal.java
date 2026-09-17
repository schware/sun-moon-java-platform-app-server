package com.sunmoon.appserver.domain.order;

/**
 * 주문을 받지 못한 이유.
 *
 * <p>{@link com.sunmoon.appserver.domain.entry.EntryRefusal} 과 같은
 * 생각입니다 — 이유마다 손님이 할 수 있는 일이 다르므로, "주문할 수
 * 없습니다" 한 줄로 뭉뚱그리지 않습니다.
 *
 * <p>HTTP 상태를 값마다 따로 두는 것도 그래서입니다. 화면은 상태 코드로
 * 갈라 그리고, 그 갈래가 사라지면 손님이 기다리면 될 일에도 직원을
 * 부릅니다.
 */
public enum OrderRefusal {

    /** 요청에 빠진 값이 있습니다. 화면의 잘못이지 손님의 잘못이 아닙니다. */
    INVALID_REQUEST(400, "주문 정보가 올바르지 않습니다. 화면을 새로고침해주세요."),

    /** QR이나 매장이 확인되지 않습니다 — 게이트가 막은 것입니다. */
    ENTRY_REFUSED(409, "지금 이 자리로는 주문할 수 없습니다."),

    /** 그 매장의 오늘 메뉴판에 없는 메뉴입니다. */
    UNKNOWN_MENU(404, "존재하지 않는 메뉴입니다."),

    /** 오늘 준비되지 않은 메뉴이거나 그날 가격이 없습니다. */
    NOT_ORDERABLE(409, "지금은 주문할 수 없는 메뉴입니다."),

    /**
     * 결제가 거절됐습니다 (§4.10).
     *
     * <p>402 Payment Required 입니다 — 이 코드가 뜻하는 바가 정확히
     * 이것이고, 화면이 "다른 수단으로"를 그릴 자리를 상태 코드만 보고
     * 고를 수 있어야 합니다.
     */
    PAYMENT_REFUSED(402, "결제가 되지 않았습니다. 다른 결제수단으로 시도해주세요."),

    /**
     * OMS가 거절했습니다. 이유는 OMS가 압니다 — 마감했거나, 매장이
     * 답하지 않았거나. 그 문장을 그대로 손님에게 전달합니다.
     */
    OMS_REJECTED(409, "주문이 접수되지 않았습니다."),

    /**
     * 취소할 수 없는 주문입니다 — 대개 매장이 이미 수락했습니다.
     *
     * <p>판단은 OMS가 합니다. 손님에게는 "이미 준비 중이라 취소가
     * 안 됩니다"에 해당하고, 직원을 부르면 되는 경우입니다.
     */
    CANCEL_REFUSED(409, "이미 준비 중이라 취소할 수 없습니다. 직원에게 문의해주세요."),

    /**
     * OMS에 닿지 못했습니다. 거절과 갈라야 합니다 — 거절은 답을 받은
     * 것이고 이건 못 받은 것이라, 다시 보내면 될 수도 있습니다.
     */
    OMS_UNREACHABLE(503, "잠시 후 다시 시도해주세요.");

    private final int status;
    private final String message;

    OrderRefusal(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int status() {
        return status;
    }

    public String message() {
        return message;
    }
}
