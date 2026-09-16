package com.sunmoon.appserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI appServerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("sun-moon-java-platform-app-server API")
                        .description("App과 QR의 마스터 관리 — 손님 쪽 창구. "
                                + "단말에게 POS-SERVER가 하는 일을, 손님 앱에게 이 서버가 합니다.")
                        .version("0.1.0"))
                // Never the server's IP — see docs/CONVENTIONS §3.
                .servers(List.of(new Server().url("/app-server")));
    }
}
