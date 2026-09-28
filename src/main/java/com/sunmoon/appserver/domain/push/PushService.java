package com.sunmoon.appserver.domain.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunmoon.appserver.domain.member.MemberDeviceRepository;
import com.sunmoon.appserver.domain.member.MemberNo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * OMS 의 order-events 한 통 → 그 손님의 기기들에 알림 (customer-app.md §4.1).
 *
 * <pre>
 *   이벤트의 orderId 로 원장 찾기(oms_order_no) → customerId 가 회원번호인가
 *     → 그 회원의 살아 있는 기기 중 push_token 있는 것 → FCM
 * </pre>
 *
 * <p><b>Order 를 안 고칩니다</b> — 이벤트에 customerId 가 이미 실려 있고, 회원 주문이면 그것이
 * 회원번호입니다. FCM 이 "없는 토큰" 이라고 하면 그 토큰을 지웁니다.
 */
public final class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    /** 원장에서 알림에 쓸 값 — oms 주문번호로. 우리 원장에 없는 주문(POS·KIOSK)은 비어 있음. */
    public interface Targets {
        Optional<PushComposer.Order> byOmsOrderNo(String omsOrderNo);
    }

    private final Targets targets;
    private final MemberDeviceRepository devices;
    private final PushSender sender;
    private final ObjectMapper json = new ObjectMapper();

    public PushService(Targets targets, MemberDeviceRepository devices, PushSender sender) {
        this.targets = targets;
        this.devices = devices;
        this.sender = sender;
    }

    /** @return 보낸 통 수 */
    public int onEvent(String eventJson) {
        JsonNode event;
        try {
            event = json.readTree(eventJson);
        } catch (Exception e) {
            return 0;
        }
        String customer = event.path("customerId").asText(null);
        if (!MemberNo.looksValid(customer)) {
            return 0;   // 익명 주문 — 보낼 곳이 없습니다
        }
        List<String> tokens = devices.pushTokens(customer);
        if (tokens.isEmpty()) {
            return 0;
        }
        Optional<PushComposer.Order> order = targets.byOmsOrderNo(event.path("orderId").asText(""));
        if (order.isEmpty()) {
            return 0;
        }
        Optional<PushComposer.Message> message = PushComposer.of(
                event.path("status").asText(null), event.path("previousStatus").asText(null), order.get());
        if (message.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (String token : tokens) {
            PushSender.Result result = sender.send(token, message.get());
            if (result == PushSender.Result.SENT) {
                sent++;
            } else if (result == PushSender.Result.UNREGISTERED) {
                devices.clearPushToken(token);
                log.info("앱이 지워진 기기의 푸시 토큰을 지웠습니다 ({})", MemberNo.masked(customer));
            }
        }
        return sent;
    }
}
