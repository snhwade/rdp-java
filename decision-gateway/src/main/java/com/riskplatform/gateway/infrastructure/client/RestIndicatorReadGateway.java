package com.riskplatform.gateway.infrastructure.client;

import com.riskplatform.gateway.domain.IndicatorReadGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;

public class RestIndicatorReadGateway implements IndicatorReadGateway {

    private static final Logger log = LoggerFactory.getLogger(RestIndicatorReadGateway.class);

    private final RestClient restClient;
    private final String baseUrl;

    public RestIndicatorReadGateway(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public double read(String refName, String dimensionKey, int windowDays, String granularity) {
        try {
            Map<String, Object> resp = restClient.get()
                    .uri(baseUrl + "/api/v1/indicators/{ref}?dimensionKey={dim}&windowDays={w}&granularity={g}",
                            refName, dimensionKey, windowDays, granularity)
                    .retrieve()
                    .body(Map.class);
            if (resp != null && resp.get("value") instanceof Number n) {
                return n.doubleValue();
            }
            return 0.0;
        } catch (Exception ex) {
            log.warn("Agent 读取指标失败 ref={} dim={}: {}", refName, dimensionKey, ex.getMessage());
            return 0.0;
        }
    }
}
