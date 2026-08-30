package com.riskplatform.gateway.infrastructure.client;

import com.riskplatform.gateway.domain.AiAdviseResult;
import com.riskplatform.gateway.domain.AiAgentPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 本地启发式 AI Agent（异步旁路占位实现）。
 *
 * <p>模拟 Agent 工具调用与自主推理：读取上下文特征、名单命中信号，产出结构化决策与 trace。
 * 后续可替换为调用 ai-training-service / LLM Agent 的实现。
 */
public class HeuristicAiAgent implements AiAgentPort {

    @Override
    public AiAdviseResult advise(
            String eventId,
            String eventTypeCode,
            Map<String, Object> context,
            String engineDecision) {
        List<Map<String, Object>> trace = new ArrayList<>();
        Map<String, Object> ctx = context == null ? Map.of() : context;

        trace.add(toolStep("read_context", Map.of("eventTypeCode", eventTypeCode)));

        boolean blackHit = isTrue(ctx.get("blackHit"));
        boolean watchHit = isTrue(ctx.get("watchHit"));
        trace.add(toolStep("check_list_signals", Map.of("blackHit", blackHit, "watchHit", watchHit)));

        double amount = parseAmount(ctx.get("amount"));
        trace.add(toolStep("read_feature", Map.of("amount", amount)));

        String decision = "PASS";
        double confidence = 0.75;
        String reason = "上下文无显著风险信号";

        if (blackHit) {
            decision = "REJECT";
            confidence = 0.95;
            reason = "名单黑名单命中";
        } else if (amount > 100000) {
            decision = "REVIEW";
            confidence = 0.88;
            reason = "交易金额超过 10 万，建议人工复核";
        } else if (watchHit) {
            decision = "REVIEW";
            confidence = 0.82;
            reason = "关注名单命中";
        } else if ("REJECT".equalsIgnoreCase(engineDecision)) {
            decision = "REVIEW";
            confidence = 0.7;
            reason = "引擎已拒绝，Agent 建议复核引擎结论";
        }

        trace.add(toolStep("reason", Map.of(
                "decision", decision,
                "confidence", confidence,
                "engineDecision", engineDecision)));

        return new AiAdviseResult(decision, confidence, reason, trace);
    }

    private static Map<String, Object> toolStep(String tool, Map<String, Object> output) {
        Map<String, Object> step = new HashMap<>();
        step.put("tool", tool);
        step.put("output", output);
        return step;
    }

    private static boolean isTrue(Object flag) {
        return Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag));
    }

    private static double parseAmount(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
