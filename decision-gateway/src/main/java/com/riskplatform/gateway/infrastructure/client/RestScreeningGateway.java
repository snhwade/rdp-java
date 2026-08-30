package com.riskplatform.gateway.infrastructure.client;

import com.riskplatform.gateway.domain.ScreeningGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 名称模糊筛查 REST 客户端（R11）。
 */
public class RestScreeningGateway implements ScreeningGateway {

    private static final Logger log = LoggerFactory.getLogger(RestScreeningGateway.class);

    private final RestClient restClient;
    private final String baseUrl;

    public RestScreeningGateway(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public HitKind screenName(String subjectName) {
        if (subjectName == null || subjectName.isBlank()) {
            return HitKind.NONE;
        }
        try {
            Map<String, Object> resp = restClient.post()
                    .uri(baseUrl + "/api/v1/screening")
                    .body(Map.of("subjectName", subjectName))
                    .retrieve()
                    .body(Map.class);
            if (resp == null || resp.get("outcome") == null) {
                return HitKind.UNAVAILABLE;
            }
            String outcome = String.valueOf(resp.get("outcome"));
            if (!"HIT".equalsIgnoreCase(outcome)) {
                if ("NO_HIT".equalsIgnoreCase(outcome) || "MISS".equalsIgnoreCase(outcome)) {
                    return HitKind.NONE;
                }
                return HitKind.UNAVAILABLE;
            }
            String listType = resp.get("listType") == null ? null : String.valueOf(resp.get("listType"));
            if ("WATCH".equalsIgnoreCase(listType)) {
                return HitKind.WATCH;
            }
            return HitKind.BLACK;
        } catch (Exception ex) {
            log.warn("筛查服务调用失败，降级为 UNAVAILABLE: subject={} 原因={}", subjectName, ex.getMessage());
            return HitKind.UNAVAILABLE;
        }
    }
}
