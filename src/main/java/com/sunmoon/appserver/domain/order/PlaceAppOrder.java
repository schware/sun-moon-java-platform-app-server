package com.sunmoon.appserver.domain.order;

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
 */
public record PlaceAppOrder(
        String storeId,
        String menuId,
        String qrCode,
        String requestId) {

    /** QR로 들어왔는가 — 아니면 매장 선택 화면입니다. */
    public boolean fromQr() {
        return qrCode != null && !qrCode.isBlank();
    }

    public OrderChannel channel() {
        return fromQr() ? OrderChannel.QR : OrderChannel.APP;
    }
}
