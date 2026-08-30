package com.riskplatform.gateway.domain;

import java.util.Locale;

/**
 * 按采纳模式合并引擎轨决策与 AI 决策。
 */
public final class AiDecisionMerger {

    private AiDecisionMerger() {
    }

    /**
     * @param engineTrack 引擎决策经名单/筛查合并后的轨
     * @param outcome     同步 AI 结果；SHADOW 不应调用本方法
     */
    public static String merge(AdoptionMode mode, String engineTrack, AiAdviseOutcome outcome) {
        String engine = normalize(engineTrack);
        if (mode == null || mode == AdoptionMode.SHADOW) {
            return engine;
        }
        if (outcome == null || !outcome.success() || outcome.decisionOrNull() == null) {
            return engine;
        }
        String ai = normalize(outcome.decisionOrNull());
        return switch (mode) {
            case ADVISORY -> mergeAdvisory(engine, ai);
            case STRICT -> strictest(engine, ai);
            case OVERRIDE -> ai;
            case SHADOW -> engine;
        };
    }

    /** AI 更严时最多抬升到 REVIEW，不允许 AI 单独导致 REJECT。 */
    private static String mergeAdvisory(String engine, String ai) {
        if (strictness(ai) <= strictness(engine)) {
            return engine;
        }
        if ("REJECT".equals(ai) || "REVIEW".equals(ai)) {
            return strictest(engine, "REVIEW");
        }
        return engine;
    }

    public static String normalize(String decision) {
        if (decision == null || decision.isBlank()) {
            return "PASS";
        }
        String d = decision.toUpperCase(Locale.ROOT);
        if ("MANUAL_REVIEW".equals(d)) {
            return "REVIEW";
        }
        return d;
    }

    public static String strictest(String a, String b) {
        return strictness(a) >= strictness(b) ? normalize(a) : normalize(b);
    }

    public static int strictness(String decision) {
        return switch (normalize(decision)) {
            case "REJECT" -> 3;
            case "REVIEW" -> 2;
            case "PASS" -> 1;
            default -> 0;
        };
    }
}
