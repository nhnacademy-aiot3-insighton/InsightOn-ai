package com.insighton.ai.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aiServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("InsightOn AI/분석 Service API")
                        .description("정각 통계 결산, 실시간 제어 제안, LLM 리포트 조회 API")
                        .version("v1.0.0."))
                .addServersItem(new Server().url("https://insighton.store").description("배포 환경 (Gateway 경유)"))
                .addServersItem(new Server().url("http://localhost:8080").description("로컬 개발 환경"));
    }
}
