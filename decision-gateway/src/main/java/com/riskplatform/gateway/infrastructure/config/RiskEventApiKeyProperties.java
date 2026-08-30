package com.riskplatform.gateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 事中决策入口可选 API Key 鉴权。
 *
 * <p>未配置 {@code api-key} 时保持开发环境放行；生产环境通过环境变量
 * {@code SECURITY_RISK_EVENTS_API_KEY} 注入后，调用方须在请求头携带匹配密钥。
 */
@ConfigurationProperties(prefix = "security.risk-events")
public class RiskEventApiKeyProperties {

    /** 为空时不启用 API Key 校验。 */
    private String apiKey = "";

    /** 请求头名称，默认 {@code X-Risk-Api-Key}。 */
    private String header = "X-Risk-Api-Key";

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }
}
