package com.sunmoon.appserver.infrastructure.push;

import com.sunmoon.appserver.domain.member.MemberDeviceRepository;
import com.sunmoon.appserver.domain.push.PushComposer;
import com.sunmoon.appserver.domain.push.PushSender;
import com.sunmoon.appserver.domain.push.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 손님 앱 푸시 (customer-app.md §4) — 배선.
 *
 * <p>둘 다 <b>설정이 있어야만</b> 켜집니다. 설정이 없는 서버에서 죽으면 안 됩니다.
 * <ul>
 *   <li>{@code PUSH_EVENTS_REDIS=true} — order-events 를 듣습니다(Order 의 EVENTS_REDIS 와 같은 모양).</li>
 *   <li>{@code FCM_PROJECT_ID} + {@code FCM_CREDENTIALS_FILE} — 없으면 보내지 않고 기동 로그에 한 번.</li>
 * </ul>
 */
@Configuration
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    public PushSender pushSender(@Value("${app.push.fcm.project-id:}") String projectId,
                                 @Value("${app.push.fcm.credentials-file:}") String credentialsFile) {
        if (projectId.isBlank() || credentialsFile.isBlank()) {
            log.info("푸시: FCM 설정이 없어 보내지 않습니다 (FCM_PROJECT_ID, FCM_CREDENTIALS_FILE)");
            return (token, message) -> PushSender.Result.OFF;
        }
        try {
            PushSender fcm = new FcmPushSender(projectId, Path.of(credentialsFile));
            log.info("푸시: FCM 프로젝트 {} 로 보냅니다", projectId);
            return fcm;
        } catch (Exception e) {
            log.error("푸시: FCM 설정을 읽지 못했습니다 — 보내지 않습니다 ({})", credentialsFile, e);
            return (token, message) -> PushSender.Result.OFF;
        }
    }

    @Bean
    public PushService pushService(JdbcTemplate jdbc, MemberDeviceRepository devices, PushSender sender) {
        PushService.Targets targets = omsOrderNo -> jdbc.query("""
                SELECT (SELECT s.name FROM v_bo_store s WHERE s.store_id = h.store_id) AS store_name,
                       m.pickup_no, m.claim_token,
                       EXISTS (SELECT 1 FROM app_order_payment p
                                WHERE p.app_order_id = h.id AND p.payment_status = 'REFUNDED') AS refunded
                  FROM app_oms_order_map m JOIN app_order_header h ON h.id = m.app_order_id
                 WHERE m.oms_order_no = ?
                """, (rs, i) -> new PushComposer.Order(rs.getString("store_name"), rs.getString("pickup_no"),
                rs.getString("claim_token"), rs.getBoolean("refunded")), omsOrderNo).stream().findFirst();
        return new PushService(targets, devices, sender);
    }

    /** order-events 구독 — PUSH_EVENTS_REDIS=true 일 때만. 끊긴 동안의 이벤트는 잃습니다(앱은 열 때 이력을 다시 읽음). */
    @Bean
    @ConditionalOnProperty(name = "app.push.events-redis", havingValue = "true")
    public RedisMessageListenerContainer orderEventsForPush(RedisConnectionFactory redis, PushService push) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redis);
        container.addMessageListener((message, pattern) -> {
            try {
                push.onEvent(new String(message.getBody(), StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                // 이벤트 하나가 구독을 죽이면 안 됩니다.
                log.warn("푸시 이벤트를 처리하지 못했습니다", e);
            }
        }, new ChannelTopic("order-events"));
        log.info("푸시: order-events 를 듣습니다");
        return container;
    }
}
