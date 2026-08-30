package com.riskplatform.engine.domain.rulepackage;

/**
 * 规则包执行定义加载端口（在线决策面，扩展阶段 R6.2）。
 *
 * <p>领域层定义、基础设施层实现：按规则包 id 从配置侧（rule-config 拥有的表，引擎共享同一库）加载
 * 规则包的<strong>完整</strong>执行定义，统一为 {@link RulePackageDefinition} 供
 * {@link RulePackageExecutor} 在决策流「规则包节点」中执行。
 *
 * <p>与试运行端口 {@code DryRunTargetPort} 的区别：试运行不输出真实策略（R5.2），加载时刻意置空策略；
 * 而在线规则包节点需要把命中规则/区间映射的<strong>策略一并并入决策流累计结果</strong>（R6.2），
 * 因此本端口的实现会加载规则绑定策略（rule_strategy）与评分区间绑定策略（score_band_strategy）。
 */
public interface RulePackageDefinitionPort {

    /**
     * 加载规则包执行定义（含策略绑定）。
     *
     * @param packageId 规则包 id（决策流节点 refId）
     * @return 规则包执行定义；规则包不存在/已下线时返回 {@code null}（由调用方按运行期降级处理 R6.4/R6.6）
     */
    RulePackageDefinition load(long packageId);
}
