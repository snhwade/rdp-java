package com.riskplatform.engine.infrastructure.client;

import com.riskplatform.engine.domain.list.ListCheckPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 调用 screening-service {@code GET /api/v1/lists/check} 做精确名单检查。
 */
public class RestListCheckClient implements ListCheckPort {

    private static final Logger log = LoggerFactory.getLogger(RestListCheckClient.class);

    private static final String[] DIMENSION_KEYS = {
            "merchantId", "idNo", "accountNo",
            "subjectName", "payerName", "counterpartyName", "name"
    };

    private final RestClient restClient;
    private final String baseUrl;

    public RestListCheckClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListHit check(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return ListHit.empty();
        }
        boolean blackHit = false;
        boolean watchHit = false;
        boolean whiteHit = false;
        boolean anyCall = false;
        Set<String> seen = new HashSet<>();

        for (String key : DIMENSION_KEYS) {
            Object raw = context.get(key);
            if (raw == null || String.valueOf(raw).isBlank()) {
                continue;
            }
            String value = String.valueOf(raw).trim();
            String sig = key + "\0" + value;
            if (!seen.add(sig)) {
                continue;
            }
            try {
                Map<String, Object> resp = restClient.get()
                        .uri(baseUrl + "/api/v1/lists/check?dimension={d}&value={v}", key, value)
                        .retrieve()
                        .body(Map.class);
                anyCall = true;
                if (resp == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(resp.get("blackHit"))) {
                    blackHit = true;
                }
                if (Boolean.TRUE.equals(resp.get("watchHit"))) {
                    watchHit = true;
                }
                if (Boolean.TRUE.equals(resp.get("whiteHit"))) {
                    whiteHit = true;
                }
            } catch (Exception ex) {
                log.warn("名单检查调用失败 dimension={} value={} 原因={}", key, value, ex.getMessage());
            }
        }
        if (!anyCall) {
            return ListHit.empty();
        }
        return new ListHit(blackHit, watchHit, whiteHit, true);
    }
}
