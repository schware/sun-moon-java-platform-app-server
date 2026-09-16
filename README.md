# sun-moon-java-platform-app-server

**APP-SERVER** — 손님 쪽 창구. App과 QR의 마스터를 관리합니다.

Spring Boot 3.3.4, embedded Jetty, 포트 **8086**, context-path
**`/app-server`**. `sun-moon-java-platform` 계열의 여섯 번째 service입니다.

## 왜 있나 — POS-SERVER와 대칭입니다

```
            BO   기준정보 (사람이 고침)
             │
    ┌────────┴────────┐        ← 마스터를 읽음 (postgres_fdw 뷰, ADR-0008)
    │                 │
APP-SERVER        POS-SERVER
 QR·앱 관리        단말 연결
    │                 │
    └────── OMS ──────┘        ← 주문·매출이 정착
         손님          단말
```

POS-SERVER(Device Server)가 단말에게 하는 일을 이 서버가 손님 앱에게
합니다 — 한 곳으로 들어오고, 마스터를 읽어 내보내고, 주문은 OMS로
넘깁니다.

**주문 경로는 바뀌지 않습니다.** `APP → OMS → POS 수락 → KDS 표시` 그대로
입니다. 이 서버는 그 사이에 끼는 층이 아닙니다.

## 연결을 쥐지 않습니다

단말과 손님은 성질이 정반대입니다:

| | 단말 (POS-SERVER) | 손님 앱 (여기) |
|---|---|---|
| 수 | 수십~수백 | **수만** |
| 수명 | 하루 종일 켜짐 | 잠깐 왔다 감 |
| 연결 유지 | **맞음** | **비용만 됨** |

그래서 상태는 **폴링**으로 확인하고, 진짜 알림이 필요해지면 그것은
WebSocket이 아니라 푸시 알림(FCM/APNs)입니다. 자세한 이유는 umbrella
`docs/adr/0007`.

## 앞으로 할 일 (방향성)

- **App** — Login, 메뉴 확인
- **QR** — 상태 확인, 메뉴 확인
- **마스터** — BO의 메뉴·테이블·공통코드를 읽어(소유하지 않음) 결품을
  걸러내고 예상 시간을 붙여 내보내기

QR은 한 종류가 아닙니다. **테이블 QR**(장소를 가리키고 인쇄돼 영구히
붙음)과 **주문 QR**(주문 한 건을 가리키는 일회성)은 수명과 식별 대상이
정반대라 코드 체계를 나눕니다 — ADR-0007.

## 아직 없는 것

- **데이터베이스.** App·QR 마스터를 소유할 예정이지만 표의 모양이 아직
  정해지지 않았습니다. 빈 데이터베이스를 미리 만들어 두는 것보다 첫 표가
  생길 때 함께 붙이는 쪽이 낫습니다. 그때 `starter-jdbc`와 `postgresql`도
  같이 들어옵니다 — 지금 선언해 두면 datasource URL이 없어 기동 자체가
  실패합니다.
- **BO 마스터 읽기.** postgres_fdw 뷰가 서버에 아직 없습니다 (ADR-0008).
- **화면.** 손님 화면은 아직 `sun-moon-java-platform-channel-order`(8085)에
  있습니다. 새로 생기는 기능부터 이쪽으로 옵니다.

## 빌드와 실행

```bash
./gradlew build
./gradlew bootRun          # http://localhost:8086/app-server/
```

`/` 는 Swagger UI로 리다이렉트됩니다 (`RootRedirectController`).

## 배포

이 계열의 다른 service와 같습니다 — 자세한 절차는 `Debian-Setting`의
`docs/docker.md`.

```bash
docker build --network host -t sun-moon-app-server .
docker run -d --name sun-moon-app-server --network host --memory=384m \
  --restart unless-stopped sun-moon-app-server
```

### 노출 범위 — 지금은 LAN 전용, 나중에 공개

```bash
# 지금 (2026-09-16): 개발 중이라 LAN 안에서만
sudo ufw allow from 192.168.0.0/24 to any port 8086 proto tcp
```

> ⚠️ **테이블 QR이 실제로 나가는 날 이 포트를 외부에 열어야 합니다.**
> 손님 휴대폰은 매장 wifi가 아니라 LTE로 들어옵니다. 그리고 이건 잊기
> 딱 좋은 종류의 일입니다 — 개발하는 동안은 LAN에서 전부 잘 되니까
> 아무 증상이 없습니다.
>
> **작업 PC가 같은 LAN에 있으면 확인도 안 됩니다.** 공인 IP로 호출해도
> 공유기가 내부로 되돌려 보내서(NAT loopback) 200이 나옵니다. LAN 전용인
> BO(8080)도 공인 IP로 200이 나오는 것으로 확인했고, nginx 접근 로그에
> 작업 PC가 `192.168.0.1`로 찍힙니다. **외부 접근 여부는 LTE 켠
> 휴대폰처럼 진짜 바깥에서 확인해야 합니다.**

## 규칙

이 계열의 공통 규칙은 umbrella의 `docs/CONVENTIONS.md`에 **실행 가능한
명령**으로 있습니다. 이 service는 `delivery`를 본으로 만들었습니다 —
`order`는 예외라 복사하면 안 됩니다(§4).

`PathRedactor`의 `SERVICE_NAME`을 바꾸는 것이 복사할 때 제일 잊기 쉬운
단계입니다. 안 바꾸면 health 응답에 다른 service 이름이 찍히는데, 아무도
안 읽어서 한참 모릅니다.
