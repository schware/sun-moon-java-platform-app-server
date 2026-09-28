package com.sunmoon.appserver.infrastructure.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunmoon.appserver.domain.push.PushComposer;
import com.sunmoon.appserver.domain.push.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FCM HTTP v1 (customer-app.md §4.3).
 *
 * <p>설계는 {@code google-auth-library} 를 적었지만, 쓰는 것은 "서비스 계정으로 액세스 토큰 받기"
 * 하나라 JDK 만으로 합니다 — RS256 로 서명한 JWT 를 Google 토큰 주소에 내고 1시간짜리 토큰을
 * 받습니다. 의존성 하나가 줄고, 이 서비스가 부를 수 있는 곳이 늘지 않습니다.
 *
 * <p><b>2026-09-29 기준 진짜 FCM 으로는 확인하지 못했습니다</b> — Firebase 프로젝트가 아직 없습니다.
 * 설정(FCM_PROJECT_ID, FCM_CREDENTIALS_FILE)이 없으면 이 클래스는 만들어지지 않습니다.
 */
public final class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final String projectId;
    private final String clientEmail;
    private final PrivateKey privateKey;
    private final String tokenUri;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    private String accessToken;
    private Instant accessTokenUntil = Instant.EPOCH;

    public FcmPushSender(String projectId, Path credentialsFile) throws Exception {
        JsonNode account = json.readTree(Files.readString(credentialsFile));
        this.projectId = projectId;
        this.clientEmail = account.path("client_email").asText();
        this.tokenUri = account.path("token_uri").asText("https://oauth2.googleapis.com/token");
        String pem = account.path("private_key").asText()
                .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        this.privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    @Override
    public Result send(String pushToken, PushComposer.Message message) {
        try {
            Map<String, Object> notification = Map.of("title", message.title(), "body", message.body());
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("token", pushToken);
            msg.put("notification", notification);
            msg.put("data", message.data());
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(
                            URI.create("https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("message", msg))))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                return Result.SENT;
            }
            if (res.statusCode() == 404 || res.body().contains("UNREGISTERED")) {
                return Result.UNREGISTERED;
            }
            log.warn("FCM 이 거절했습니다 ({}): {}", res.statusCode(), res.body());
            return Result.FAILED;
        } catch (Exception e) {
            log.warn("FCM 에 보내지 못했습니다", e);
            return Result.FAILED;
        }
    }

    private synchronized String accessToken() throws Exception {
        Instant now = Instant.now();
        if (accessToken != null && now.isBefore(accessTokenUntil.minusSeconds(60))) {
            return accessToken;
        }
        String header = b64(json.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
        String claims = b64(json.writeValueAsBytes(Map.of(
                "iss", clientEmail, "scope", SCOPE, "aud", tokenUri,
                "iat", now.getEpochSecond(), "exp", now.getEpochSecond() + 3600)));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        String jwt = header + "." + claims + "." + b64(signer.sign());
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(tokenUri))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type="
                        + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                        + "&assertion=" + jwt))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("FCM 액세스 토큰을 받지 못했습니다 (" + res.statusCode() + ")");
        }
        JsonNode body = json.readTree(res.body());
        accessToken = body.path("access_token").asText();
        accessTokenUntil = now.plusSeconds(body.path("expires_in").asLong(3600));
        return accessToken;
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
