package com.riskplatform.engine.application;

/**
 * 决策可观测性指标埋点端口（R15.2）。
 *
 * <p>抽象出监控指标记录能力，使决策链路与具体监控实现（Micrometer/Prometheus）解耦，
 * 便于在无监控注册表的单元测试中以替身验证埋点调用。需暴露的指标：
 * <ul>
 *   <li>事件处理量（计数）；</li>
 *   <li>决策耗时（计时，用于 P50/P99 分位）；</li>
 *   <li>规则命中率（命中数 / 执行数派生）。</li>
 * </ul>
 */
public interface DecisionMetrics {

    /**
     * 记录一次事件处理及其决策结果与耗时。
     *
     * @param decision 最终决策（如 PASS/REVIEW/REJECT，作为标签维度）
     * @param elapsedMs 决策耗时（毫秒）
     */
    void recordEvent(String decision, long elapsedMs);

    /**
     * 记录一次规则组执行的命中情况，用于派生规则命中率。
     *
     * @param executedRules 本次执行的规则数
     * @param hitRules      其中命中的规则数
     */
    void recordRuleExecution(int executedRules, int hitRules);
}
