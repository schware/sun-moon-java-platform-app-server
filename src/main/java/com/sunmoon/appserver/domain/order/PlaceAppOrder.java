package com.sunmoon.appserver.domain.order;

import java.util.List;

/**
 * 손님이 보낸 주문 요청.
 *
 * <p>{@code requestId} 가 멱등성 키입니다 (업무 정의서 §4.4.6). 손님이
 * 버튼을 두 번 누르거나 네트워크 재시도가 겹쳐도 주문이 둘이 되지
 * 않게 하는 값이고, <b>화면이 만들어 보냅니다</b> — 서버가 만들면 재시도
 * 때마다 새 값이 되어 아무것도 막지 못합니다.
 *
 * <p>{@code storeId} 는 {@code qrCode} 가 있으면 <b>무시됩니다</b>.
 * 자리는 서버가 코드를 풀어서 정합니다 — 브라우저가 보낸 매장을 믿으면
 * 값을 바꿔서 앉지도 않은 자리로 주문을 보낼 수 있고, QR의 요점이
 * 사라집니다.
 *
 * <p><b>장바구니 (2026-09-18).</b> {@code items} 로 여러 줄을 보냅니다.
 * {@code menuId} 한 개짜리 옛 모양도 그대로 받는 이유는 화면과 서버가
 * 한날한시에 바뀌지 않기 때문입니다 — 서버만 먼저 올라가도 손님 화면이
 * 안 깨져야 합니다. 둘 다 오면 {@code items} 가 이깁니다.
 */
public record PlaceAppOrder(
        String storeId,
        /** 옛 모양 — 메뉴 하나. {@code items} 가 있으면 무시됩니다. */
        String menuId,
        /** 새 모양 — 장바구니. */
        List<Item> items,
        String qrCode,
        String requestId) {

    /**
     * 장바구니 한 줄.
     *
     * <p><b>가격을 받지 않습니다.</b> 손님 화면이 보낸 가격을 믿으면
     * 값을 바꿔서 1원에 살 수 있습니다. 가격은 서버가 그 매장 그 날짜의
     * 메뉴판에서 다시 꺼냅니다 — 손님이 메뉴를 고르는 동안 가격이 바뀌었을
     * 수도 있고, 그때 맞는 값은 주문이 접수되는 순간의 값입니다.
     */
    public record Item(String menuId, int quantity) {

        public Item {
            if (menuId == null || menuId.isBlank()) {
                throw new IllegalArgumentException("menuId is required");
            }
            // 수량을 안 보내면 1입니다. 0을 그대로 받으면 아무것도 안
            // 시킨 줄이 주문에 남습니다.
            quantity = quantity <= 0 ? 1 : quantity;
        }
    }

    public PlaceAppOrder {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 한 줄짜리 옛 모양으로 만들기 — 시험과 옛 호출자용. */
    public static PlaceAppOrder single(String storeId, String menuId, String qrCode, String requestId) {
        return new PlaceAppOrder(storeId, menuId, List.of(), qrCode, requestId);
    }

    /**
     * 이 요청이 실제로 담고 있는 줄들.
     *
     * <p>같은 메뉴를 두 줄로 보내도 <b>합치지 않습니다</b>. 나중에 줄마다
     * 옵션이 붙으면(하나는 맵게, 하나는 안 맵게) 합쳐 버린 주문은 되돌릴
     * 수 없습니다.
     */
    public List<Item> orderedItems() {
        if (!items.isEmpty()) {
            return items;
        }
        if (menuId == null || menuId.isBlank()) {
            return List.of();
        }
        return List.of(new Item(menuId, 1));
    }

    /** QR로 들어왔는가 — 아니면 매장 선택 화면입니다. */
    public boolean fromQr() {
        return qrCode != null && !qrCode.isBlank();
    }

    public OrderChannel channel() {
        return fromQr() ? OrderChannel.QR : OrderChannel.APP;
    }
}
