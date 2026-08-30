package com.riskplatform.engine.domain.rulepackage;

/**
 * 规则三态运行语义（执行侧，R7.3–R7.7）。
 *
 * <p>对应配置侧 {@code rule_v2.status} 的三态取值 {@code ONLINE/TRIAL_RUN/OFFLINE}（V22 迁移
 * 已将历史 {@code ENABLED→ONLINE}、{@code DISABLED→OFFLINE}）。引擎加载规则包执行定义时据此：
 * <ul>
 *   <li>{@link #OFFLINE}：跳过，不进入执行集（R7.3/R7.4）；</li>
 *   <li>{@link #ONLINE}：进入执行集，命中参与最终决策聚合（R7.6）；</li>
 *   <li>{@link #TRIAL_RUN}：进入执行集并在命中明细中返回，但不参与最终决策聚合（R7.5/R7.6/R7.7）。</li>
 * </ul>
 *
 * <p>为兼容历史数据与脏数据，解析时同时识别旧两态字面量，并对未知/空值按 {@link #OFFLINE} 保守处理
 * （加载阶段过滤而非运行期异常）。
 */
public enum RuleRuntimeStatus {

    ONLINE,
    TRIAL_RUN,
    OFFLINE;

    /**
     * 解析配置库中的状态字面量为执行侧三态语义。
     *
     * @param raw {@code rule_v2.status} 原始值（可空）
     * @return 三态语义；未知/空值按 {@link #OFFLINE} 处理
     */
    public static RuleRuntimeStatus parse(String raw) {
        if (raw == null) {
            return OFFLINE;
        }
        String v = raw.trim().toUpperCase();
        switch (v) {
            case "ONLINE":
            case "ENABLED":
                return ONLINE;
            case "TRIAL_RUN":
            case "TRIAL":
                return TRIAL_RUN;
            case "OFFLINE":
            case "DISABLED":
                return OFFLINE;
            default:
                return OFFLINE;
        }
    }

    /** 是否进入执行集（上线或试运行进入，下线跳过，R7.3/R7.4）。 */
    public boolean executable() {
        return this == ONLINE || this == TRIAL_RUN;
    }

    /** 是否为试运行（命中仅入明细、不参与最终决策聚合，R7.6/R7.7）。 */
    public boolean trialRun() {
        return this == TRIAL_RUN;
    }
}
