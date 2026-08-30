package com.riskplatform.gateway.infrastructure.client;

import com.riskplatform.gateway.domain.RiskEventValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 事件类型状态检查（REST 实现，R2.3/R2.4）。
 *
 * <p>调用规则配置服务的 {@code GET /api/v1/event-types} 列表，判断指定 code 是否存在且启用。
 * 规则配置服务不可达时 fail-closed：视为事件类型不存在，拒绝受理。
 */
public class RestEventTypeStatusChecker implements RiskEventValidator.EventTypeStatusChecker {

    private static final Logger log = LoggerFactory.getLogger(RestEventTypeStatusChecker.class);

    private final RestClient restClient;
    private final String baseUrl;

    public RestEventTypeStatusChecker(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Status check(String eventTypeCode) {
        try {
            List<Map<String, Object>> list = restClient.get()
                    .uri(baseUrl + "/api/v1/event-types")
                    .retrieve()
                    .body(List.class);
            if (list == null) {
                log.warn("规则配置服务返回空事件类型列表，fail-closed: eventTypeCode={}", eventTypeCode);
                return Status.NOT_FOUND;
            }
            for (Map<String, Object> et : list) {
                if (eventTypeCode.equals(String.valueOf(et.get("code")))) {
                    String status = String.valueOf(et.get("status"));
                    return "ENABLED".equals(status) ? Status.ENABLED : Status.DISABLED;
                }
            }
            return Status.NOT_FOUND;
        } catch (Exception ex) {
            log.error("规则配置服务不可达，fail-closed: eventTypeCode={}", eventTypeCode, ex);
            return Status.NOT_FOUND;
        }
    }
}
