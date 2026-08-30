package com.riskplatform.ruleconfig.domain.rulev2;

/**
 * 结构化规则三态状态（risk-console-redesign，R7.1）。
 *
 * <p>对应表 {@code rule_v2.status}，由原两态 {@code ENABLED/DISABLED} 扩展为三态：
 * <ul>
 *   <li>{@link #ONLINE} 上线：被执行且参与最终决策聚合。</li>
 *   <li>{@link #TRIAL_RUN} 试运行：被执行并返回结果用于观察效果，但不参与最终决策聚合。</li>
 *   <li>{@link #OFFLINE} 下线（默认）：不被执行。</li>
 * </ul>
 *
 * <p>数据迁移（R7.8，Flyway V22）：既有 {@code ENABLED→ONLINE}、{@code DISABLED→OFFLINE}。
 */
public enum RuleV2Status {
    /** 上线（参与最终决策聚合）。 */
    ONLINE,
    /** 试运行（被执行并返回结果，但不参与最终决策聚合）。 */
    TRIAL_RUN,
    /** 下线（不被执行，默认）。 */
    OFFLINE
}
