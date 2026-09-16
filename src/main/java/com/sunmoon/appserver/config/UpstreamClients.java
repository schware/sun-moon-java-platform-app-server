package com.sunmoon.appserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 이 서버가 HTTP로 읽는 유일한 곳 — OMS(Order)의 영업 상태.
 *
 * <p>BO 마스터(메뉴·테이블·매장)는 여기 없습니다. 그쪽은 postgres_fdw
 * 뷰로 읽습니다 — 서비스가 아니라 데이터베이스를 읽기 때문에 BO 컨테이너가
 * 내려가 있어도 동작합니다 (umbrella {@code docs/adr/0008}).
 *
 * <p>타임아웃이 짧습니다. 손님이 QR을 찍고 기다리는 화면이라, 느리게
 * 성공하는 것보다 빨리 "잠시 후 다시" 가 낫습니다.
 */
@Configuration
public class UpstreamClients {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(4);

    @Bean
    public RestClient orderClient(@Value("${app-server.order.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
