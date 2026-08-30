package com.riskplatform.bff;

import com.riskplatform.gateway.infrastructure.config.AgentLlmProperties;
import com.riskplatform.common.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Admin BFF / 网关启动入口。
 *
 * <p>standalone 模式（默认）：嵌入 rule-config / screening / rating / gateway 运维 / engine / indicator，
 * 仅依赖 MySQL + Redis，管理端通过 {@code /bff/api/v1/**} 访问。
 */
@SpringBootApplication
@EnableConfigurationProperties(AgentLlmProperties.class)
@Import(GlobalExceptionHandler.class)
public class AdminBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminBffApplication.class, args);
    }
}
