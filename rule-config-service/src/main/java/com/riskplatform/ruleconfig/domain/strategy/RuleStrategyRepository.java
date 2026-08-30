package com.riskplatform.ruleconfig.domain.strategy;

import java.util.List;

/**
 * 规则-策略绑定仓储端口（R3）。由基础设施层用 MyBatis-Plus 持久化到 rule_strategy 表。
 */
public interface RuleStrategyRepository {

    /** 保存新绑定，返回带 id 的实体。 */
    RuleStrategy save(RuleStrategy ruleStrategy);

    /** 全量替换某规则的策略绑定：先删后插。 */
    void replaceByRuleV2Id(Long ruleV2Id, List<RuleStrategy> bindings);

    /** 按规则查询其策略绑定列表。 */
    List<RuleStrategy> findByRuleV2Id(Long ruleV2Id);

    /** 按策略定义查询引用它的规则绑定列表（R5.8 关联关系查询）。 */
    List<RuleStrategy> findByStrategyDefId(Long strategyDefId);
}
