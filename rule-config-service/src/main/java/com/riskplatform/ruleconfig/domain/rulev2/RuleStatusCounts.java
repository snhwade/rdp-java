package com.riskplatform.ruleconfig.domain.rulev2;

import java.util.Map;

/**
 * 规则三态计数（R6.6）。
 *
 * <p>承载某规则包下规则按状态分组聚合的条数：上线（ONLINE）、试运行（TRIAL_RUN）、下线（OFFLINE）。
 * 由 {@link RuleV2Repository#countByStatus(Long)} 按 {@code rulePackageId + status} 分组聚合得到。
 *
 * <p>状态名以字符串键承载，避免与状态枚举的演进强耦合（三态枚举的引入由并行任务负责）。
 */
public record RuleStatusCounts(long online, long trialRun, long offline) {

    public static final String ONLINE = "ONLINE";
    public static final String TRIAL_RUN = "TRIAL_RUN";
    public static final String OFFLINE = "OFFLINE";

    /** 空计数（无规则）。 */
    public static RuleStatusCounts empty() {
        return new RuleStatusCounts(0L, 0L, 0L);
    }

    /**
     * 由「状态字符串 → 计数」映射构建三态计数。
     *
     * <p>未知或历史遗留状态（如尚未迁移的 ENABLED/DISABLED）按其语义归并：
     * {@code ENABLED→上线}、{@code DISABLED→下线}，保证迁移前后计数稳定。
     */
    public static RuleStatusCounts fromStatusCounts(Map<String, Long> byStatus) {
        long online = 0L;
        long trialRun = 0L;
        long offline = 0L;
        if (byStatus != null) {
            for (Map.Entry<String, Long> e : byStatus.entrySet()) {
                String status = e.getKey() == null ? "" : e.getKey();
                long cnt = e.getValue() == null ? 0L : e.getValue();
                switch (status) {
                    case ONLINE, "ENABLED" -> online += cnt;
                    case TRIAL_RUN -> trialRun += cnt;
                    case OFFLINE, "DISABLED" -> offline += cnt;
                    default -> {
                        // 未知状态忽略，不计入任一态
                    }
                }
            }
        }
        return new RuleStatusCounts(online, trialRun, offline);
    }
}
