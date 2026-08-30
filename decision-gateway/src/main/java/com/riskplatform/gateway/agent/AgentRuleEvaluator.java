package com.riskplatform.gateway.agent;

import com.riskplatform.gateway.domain.AiAdviseResult;

import java.util.Locale;
import java.util.Map;

/**
 * 按策略 rules 配置评估启发式决策。
 */
public class AgentRuleEvaluator {

    public AiAdviseResult evaluate(AgentRuntimeConfig config, AgentToolContext ctx) {
        if (config.rules != null) {
            for (AgentRuntimeConfig.RuleConfig rule : config.rules) {
                if (rule == null || rule.when == null) {
                    continue;
                }
                if (matches(rule, ctx)) {
                    return new AiAdviseResult(
                            rule.decision == null ? config.defaultDecision : rule.decision,
                            rule.confidence,
                            rule.reason == null ? config.defaultReason : rule.reason,
                            ctx.trace());
                }
            }
        }
        return new AiAdviseResult(
                config.defaultDecision,
                config.defaultConfidence,
                config.defaultReason,
                ctx.trace());
    }

    private boolean matches(AgentRuntimeConfig.RuleConfig rule, AgentToolContext ctx) {
        String when = rule.when.toLowerCase(Locale.ROOT);
        Map<String, Object> signals = ctx.signals();
        if ("blackhit".equals(when)) {
            return isTrue(signals.get("blackHit"));
        }
        if ("watchhit".equals(when)) {
            return isTrue(signals.get("watchHit"));
        }
        if ("engine_reject".equals(when)) {
            return "REJECT".equalsIgnoreCase(ctx.engineDecision());
        }
        if ("amount_gt".equals(when)) {
            double amount = parseDouble(signals.get("amount"));
            double threshold = rule.threshold == null ? Double.MAX_VALUE : rule.threshold;
            return amount > threshold;
        }
        if ("indicator_gt".equals(when)) {
            if (rule.refName == null) {
                return false;
            }
            return isTrue(signals.get("indicator_gt:" + rule.refName));
        }
        if ("amount_spike".equals(when)) {
            return isTrue(signals.get("amountSpike"));
        }
        return false;
    }

    private static boolean isTrue(Object flag) {
        return Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag));
    }

    private static double parseDouble(Object raw) {
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
