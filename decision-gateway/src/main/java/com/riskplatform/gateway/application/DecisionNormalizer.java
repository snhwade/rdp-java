package com.riskplatform.gateway.application;

/**
 * 将引擎/决策流产出的多种决策枚举统一为业务三态 PASS/REVIEW/REJECT。
 */
public final class DecisionNormalizer {

    private DecisionNormalizer() {
    }

    public static String toBusinessDecision(String raw) {
        if (raw == null || raw.isBlank()) {
            return "PASS";
        }
        return switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "REJECT", "AUTO_REJECT" -> "REJECT";
            case "REVIEW", "MANUAL_REVIEW", "REFUND" -> "REVIEW";
            case "PASS", "AUTO_PASS" -> "PASS";
            default -> raw;
        };
    }
}
