package com.riskplatform.bff.adapter;

import com.riskplatform.bff.application.BffAggregationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 可观测聚合（enhancement-plan T7）：给控制台 observability 页提供 JSON 指标快照。
 */
@RestController
@RequestMapping("/bff/api/v1/observability")
public class ObservabilityBffController {

    private final BffAggregationService aggregation;

    public ObservabilityBffController(BffAggregationService aggregation) {
        this.aggregation = aggregation;
    }

    @GetMapping("/metrics")
    @SuppressWarnings("unchecked")
    public Map<String, Object> metrics(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        Map<String, Object> out = new HashMap<>();
        out.put("eventsTotal", null);
        out.put("decisionDurationP50Ms", null);
        out.put("decisionDurationP99Ms", null);
        out.put("ruleHitRate", null);

        try {
            Object stats = aggregation.gatewayGet("/api/v1/ai-decision-records/stats", auth);
            if (stats instanceof Map<?, ?> map) {
                Object total = map.get("total");
                if (total instanceof Number n) {
                    out.put("eventsTotal", n.longValue());
                }
                Object success = map.get("success");
                Object divergence = map.get("divergenceCount");
                if (success instanceof Number s && s.longValue() > 0 && divergence instanceof Number d) {
                    // 用「无分歧率」近似命中一致率，供看板占位；精确规则命中率仍依赖 Prometheus
                    double consistent = 1.0 - (d.doubleValue() / Math.max(s.doubleValue(), 1.0));
                    out.put("ruleHitRate", Math.max(0.0, Math.min(1.0, consistent)));
                }
                out.put("aiStats", map);
            }
        } catch (Exception ignore) {
            // 下游不可用时返回占位字段，前端展示 —
        }

        try {
            Object cache = aggregation.engineGet("/api/v1/config-cache/stats", auth);
            out.put("configCache", cache);
        } catch (Exception ignore) {
            // optional
        }
        return out;
    }
}
