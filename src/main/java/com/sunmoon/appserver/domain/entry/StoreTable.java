package com.sunmoon.appserver.domain.entry;

/**
 * BO가 소유한 테이블 기준 정보를, 이 서버가 읽은 만큼만.
 *
 * <p>좌표({@code posX}/{@code posY})는 없습니다. 손님 화면은 자리가 어디
 * 있는지 알 필요가 없고, 그건 배치도를 그리는 BO와 언젠가 음식을 옮길
 * 로봇의 관심사입니다. 읽을 수 있다고 다 실어 나르면 나중에 누가 그걸
 * 쓰기 시작합니다.
 */
public record StoreTable(
        long id,
        String storeId,
        int floor,
        String tableNo,
        String displayName,
        boolean active) {

    public String label() {
        return displayName == null || displayName.isBlank() ? tableNo : displayName;
    }
}
