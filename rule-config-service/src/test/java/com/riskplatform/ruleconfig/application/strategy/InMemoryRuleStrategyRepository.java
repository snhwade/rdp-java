package com.riskplatform.ruleconfig.application.strategy;

import com.riskplatform.ruleconfig.domain.strategy.RuleStrategy;
import com.riskplatform.ruleconfig.domain.strategy.RuleStrategyRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版规则-策略绑定仓储，用于验证策略关联关系查询（R5.8）单元测试。
 */
public class InMemoryRuleStrategyRepository implements RuleStrategyRepository {

    private final Map<Long, RuleStrategy> store = new LinkedHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public RuleStrategy save(RuleStrategy ruleStrategy) {
        long id = seq.incrementAndGet();
        ruleStrategy.assignId(id);
        store.put(id, ruleStrategy);
        return ruleStrategy;
    }

    @Override
    public void replaceByRuleV2Id(Long ruleV2Id, List<RuleStrategy> bindings) {
        store.values().removeIf(rs -> ruleV2Id.equals(rs.getRuleV2Id()));
        for (RuleStrategy rs : bindings) {
            save(rs);
        }
    }

    @Override
    public List<RuleStrategy> findByRuleV2Id(Long ruleV2Id) {
        return store.values().stream().filter(rs -> ruleV2Id.equals(rs.getRuleV2Id())).toList();
    }

    @Override
    public List<RuleStrategy> findByStrategyDefId(Long strategyDefId) {
        return new ArrayList<>(store.values().stream()
                .filter(rs -> strategyDefId.equals(rs.getStrategyDefId()))
                .toList());
    }
}
