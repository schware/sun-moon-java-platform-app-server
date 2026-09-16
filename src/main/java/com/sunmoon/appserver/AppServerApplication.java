package com.sunmoon.appserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * APP-SERVER — 손님 쪽 창구.
 *
 * <p>POS-SERVER(Device Server)가 단말에게 하는 일을, 이 서버가 손님 앱에게
 * 합니다: 한 곳으로 들어오고, 마스터를 읽어 내보내고, 주문은 OMS로
 * 넘깁니다. 주문 경로 자체는 바뀌지 않습니다 —
 * {@code APP → OMS → POS 수락 → KDS 표시}.
 *
 * <p><b>연결을 쥐지 않습니다.</b> 단말은 수십 대가 하루 종일 켜져 있어
 * POS-SERVER가 소켓을 들고 있는 것이 맞지만, 손님 폰은 수만 대가 잠깐
 * 왔다 가고 화면이 꺼지면 끊깁니다. 상태는 폴링으로 확인하고, 진짜
 * 알림이 필요해지면 그것은 WebSocket이 아니라 푸시 알림입니다.
 * umbrella {@code docs/adr/0007}.
 */
@SpringBootApplication
public class AppServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppServerApplication.class, args);
    }
}
