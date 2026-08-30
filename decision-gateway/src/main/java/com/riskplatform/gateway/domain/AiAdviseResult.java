package com.riskplatform.gateway.domain;

import java.util.List;
import java.util.Map;

/**
 * AI Agent 推理结果。
 */
public record AiAdviseResult(
        String decision,
        double confidence,
        String reason,
        List<Map<String, Object>> trace) {
}
