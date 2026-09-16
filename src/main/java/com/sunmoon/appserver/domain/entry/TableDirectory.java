package com.sunmoon.appserver.domain.entry;

import java.util.Optional;

/**
 * QR 코드로 테이블을 찾습니다. BO 마스터를 읽는 것이고, <b>읽기만</b>
 * 합니다 — 코드 발급도 수정도 BO의 일입니다.
 */
public interface TableDirectory {

    Optional<StoreTable> byQrCode(String qrCode);

    /** 매장 이름. 손님 화면에 "1매장 3번"으로 보이게 하려면 필요합니다. */
    Optional<String> storeName(String storeId);
}
