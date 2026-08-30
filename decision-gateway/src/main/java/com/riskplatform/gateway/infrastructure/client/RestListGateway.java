package com.riskplatform.gateway.infrastructure.client;

import com.riskplatform.gateway.domain.ListGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 精确名单 REST 客户端：调用 screening-service {@code GET /api/v1/lists/check}。
 */
public class RestListGateway implements ListGateway {

    private static final Logger log = LoggerFactory.getLogger(RestListGateway.class);

    /** 上下文中参与精确名单判定的字段（dimension 名与 context key 一致）。 */
    private static final String[] DIMENSION_KEYS = {
            "merchantId", "idNo", "accountNo",
            "subjectName", "payerName", "counterpartyName", "name"
    };

    private final RestClient restClient;
    private final String baseUrl;

    public RestListGateway(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListCheckSummary checkContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return ListCheckSummary.empty();
        }
        boolean blackHit = false;
        boolean watchHit = false;
        boolean whiteHit = false;
        boolean whiteImmuneAll = false;
        List<Long> immuneRuleIds = new ArrayList<>();
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
                    WhiteMerge merged = mergeWhiteRecords((List<Map<String, Object>>) resp.get("whiteRecords"));
                    whiteImmuneAll = whiteImmuneAll || merged.immuneAll();
                    for (Long id : merged.immuneRuleIds()) {
                        if (!immuneRuleIds.contains(id)) {
                            immuneRuleIds.add(id);
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("名单判定调用失败，跳过该维度: dimension={} 原因={}", key, ex.getMessage());
            }
        }
        return new ListCheckSummary(blackHit, watchHit, whiteHit, whiteImmuneAll, List.copyOf(immuneRuleIds));
    }

    private record WhiteMerge(boolean immuneAll, List<Long> immuneRuleIds) {
    }

    private static WhiteMerge mergeWhiteRecords(List<Map<String, Object>> whiteRecords) {
        if (whiteRecords == null || whiteRecords.isEmpty()) {
            return new WhiteMerge(false, List.of());
        }
        boolean immuneAll = false;
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> rec : whiteRecords) {
            if (rec == null) {
                continue;
            }
            Object immune = rec.get("immuneRuleId");
            if (immune == null) {
                immuneAll = true;
            } else {
                Long id = asLong(immune);
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return new WhiteMerge(immuneAll, ids);
    }

    private static Long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
