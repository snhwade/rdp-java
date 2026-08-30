package com.riskplatform.engine.domain.dryrun;

import com.riskplatform.engine.domain.rulepackage.RulePackageDefinition;

/**
 * 试运行目标定义加载端口（R5.2/R5.4）。
 *
 * <p>领域层定义、基础设施层实现：按目标类型与 id 从配置侧（rule-config 拥有的表）加载目标的
 * 执行定义，统一为 {@link RulePackageDefinition}：
 * <ul>
 *   <li>{@link DryRunTargetType#RULE_PACKAGE}：加载规则包（含触发模式、规则、分值区间、预警阈值）；</li>
 *   <li>{@link DryRunTargetType#RULE}：将单条结构化规则包装为「单规则命中模式包」，
 *       使其可复用规则包执行器逐条评估。</li>
 * </ul>
 *
 * <p>复用既有规则包执行器（{@code RulePackageExecutor}）即可同时支持单规则与规则包试运行。
 */
public interface DryRunTargetPort {

    /**
     * 加载目标执行定义。
     *
     * @param targetType 目标类型（RULE/RULE_PACKAGE）
     * @param targetId   目标 id（规则或规则包 id）
     * @return 规则包执行定义；目标不存在时返回 {@code null}（由调用方按任务失败处理）
     */
    RulePackageDefinition load(DryRunTargetType targetType, long targetId);
}
