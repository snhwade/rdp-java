package com.riskplatform.gateway.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 精确名单判定网关（S1）：按 dimension + value 调用 screening-service /lists/check。
 */
public interface ListGateway {

    /**
     * 精确名单命中汇总。
     *
     * @param blackHit        命中黑名单（精确）
     * @param watchHit        命中关注名单（精确）
     * @param whiteHit        命中白名单
     * @param whiteImmuneAll  白名单是否对所有规则免疫（immuneRuleId 为空）
     * @param immuneRuleIds   白名单指定的免疫规则 id 列表
     */
    record ListCheckSummary(
            boolean blackHit,
            boolean watchHit,
            boolean whiteHit,
            boolean whiteImmuneAll,
            List<Long> immuneRuleIds) {

        public static ListCheckSummary empty() {
            return new ListCheckSummary(false, false, false, false, List.of());
        }
    }

    /** 对上下文中的各维度字段做精确名单判定。 */
    ListCheckSummary checkContext(Map<String, Object> context);

    /** 将名单判定结果写入上下文副本，供引擎与决策流 LIST_CHECK 节点使用。 */
    default Map<String, Object> enrichContext(Map<String, Object> context) {
        Map<String, Object> enriched = new HashMap<>(context == null ? Map.of() : context);
        ListCheckSummary summary = checkContext(context);
        if (summary.blackHit()) {
            enriched.put("blackHit", true);
        }
        if (summary.watchHit()) {
            enriched.put("watchHit", true);
        }
        if (summary.whiteHit()) {
            enriched.put("whiteHit", true);
        }
        if (summary.whiteImmuneAll()) {
            enriched.put("whiteImmuneAll", true);
        }
        if (summary.immuneRuleIds() != null && !summary.immuneRuleIds().isEmpty()) {
            enriched.put("immuneRuleIds", summary.immuneRuleIds());
        }
        return enriched;
    }
}
