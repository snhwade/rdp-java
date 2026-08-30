package com.riskplatform.gateway.infrastructure.client;

import com.riskplatform.gateway.domain.AgentStrategyPort;
import org.springframework.web.client.RestClient;

import java.util.Map;

public class RestAgentStrategyLoader implements AgentStrategyPort {

    private final RestClient restClient;
    private final String baseUrl;

    public RestAgentStrategyLoader(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResolvedAgentStrategy resolve(String eventTypeCode) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(baseUrl + "/api/v1/agent-strategies/resolve?eventTypeCode={code}", eventTypeCode)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return null;
            }
            return new ResolvedAgentStrategy(
                    String.valueOf(body.get("code")),
                    String.valueOf(body.get("name")),
                    body.get("configJson") == null ? "{}" : String.valueOf(body.get("configJson")),
                    body.get("adoptionMode") == null ? "SHADOW" : String.valueOf(body.get("adoptionMode")));
        } catch (Exception ex) {
            return null;
        }
    }
}
