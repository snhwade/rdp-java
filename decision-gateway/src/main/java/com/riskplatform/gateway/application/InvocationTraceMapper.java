package com.riskplatform.gateway.application;

import com.riskplatform.gateway.domain.EngineDecisionRecordView;
import com.riskplatform.gateway.domain.InvocationTraceView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从引擎决策记录 + detail_json 映射执行链路视图（XT1）。 */
final class InvocationTraceMapper {

    private InvocationTraceMapper() {
    }

    static InvocationTraceView from(EngineDecisionRecordView engine) {
        Map<String, Object> detail = engine.detail() == null ? Map.of() : engine.detail();
        List<InvocationTraceView.HitDecisionView> hits = parseHits(detail.get("hits"));
        if (hits.isEmpty() && engine.engineDecision() != null) {
            // 兼容旧 detail_json：仅有 decision 字段
            hits = List.of();
        }
        return new InvocationTraceView(
                engine.eventId(),
                engine.eventId(),
                firstNonBlank(asString(detail.get("decision")), engine.finalDecision()),
                hits,
                engine.elapsedMs() == null ? 0L : engine.elapsedMs(),
                asString(detail.get("timeoutReason")),
                asString(detail.get("groupStatus")),
                asMap(detail.get("selectorMatch")),
                parseRecords(detail.get("records"), hits),
                asStringList(detail.get("flowPath")),
                parseFlowTrace(detail.get("flowTrace")));
    }

    @SuppressWarnings("unchecked")
    private static List<InvocationTraceView.HitDecisionView> parseHits(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<InvocationTraceView.HitDecisionView> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Long ruleId = asLong(map.get("ruleId"));
            if (ruleId == null) {
                continue;
            }
            out.add(new InvocationTraceView.HitDecisionView(
                    ruleId,
                    asInt(map.get("priority"), 0),
                    asString(map.get("decision")),
                    Boolean.TRUE.equals(map.get("trialRun"))));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<InvocationTraceView.RuleExecutionView> parseRecords(
            Object raw, List<InvocationTraceView.HitDecisionView> hits) {
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<InvocationTraceView.RuleExecutionView> out = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Long ruleId = asLong(map.get("ruleId"));
                if (ruleId == null) {
                    continue;
                }
                out.add(new InvocationTraceView.RuleExecutionView(
                        ruleId,
                        asInt(map.get("version"), 0),
                        Boolean.TRUE.equals(map.get("hit")),
                        Boolean.TRUE.equals(map.get("failed")),
                        asString(map.get("failReason"))));
            }
            return out;
        }
        // 兼容旧数据：由命中规则还原
        return hits.stream()
                .map(h -> new InvocationTraceView.RuleExecutionView(
                        h.ruleId(), 0, true, false, null))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<InvocationTraceView.FlowTraceStepView> parseFlowTrace(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<InvocationTraceView.FlowTraceStepView> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            out.add(new InvocationTraceView.FlowTraceStepView(
                    asString(map.get("nodeId")),
                    asString(map.get("nodeType")),
                    asString(map.get("refType")),
                    asLong(map.get("refId")),
                    parseHits(map.get("hits")),
                    asMap(map.get("assignments"))));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static String asString(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isBlank() || "null".equals(s) ? null : s;
    }

    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int asInt(Object v, int defaultValue) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? defaultValue : Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
