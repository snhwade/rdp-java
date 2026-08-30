package com.riskplatform.ruleconfig.application.strategy;

import com.riskplatform.ruleconfig.domain.strategy.ScoreBandStrategy;
import com.riskplatform.ruleconfig.domain.strategy.ScoreBandStrategyRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版评分区间-策略绑定仓储，用于验证策略关联关系查询（R5.8）单元测试。
 */
public class InMemoryScoreBandStrategyRepository implements ScoreBandStrategyRepository {

    private final Map<Long, ScoreBandStrategy> store = new LinkedHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public ScoreBandStrategy save(ScoreBandStrategy scoreBandStrategy) {
        long id = seq.incrementAndGet();
        scoreBandStrategy.assignId(id);
        store.put(id, scoreBandStrategy);
        return scoreBandStrategy;
    }

    @Override
    public void replaceByScoreBandId(Long scoreBandId, List<ScoreBandStrategy> bindings) {
        store.values().removeIf(s -> scoreBandId.equals(s.getScoreBandId()));
        for (ScoreBandStrategy s : bindings) {
            save(s);
        }
    }

    @Override
    public List<ScoreBandStrategy> findByScoreBandId(Long scoreBandId) {
        return store.values().stream().filter(s -> scoreBandId.equals(s.getScoreBandId())).toList();
    }

    @Override
    public List<ScoreBandStrategy> findByStrategyDefId(Long strategyDefId) {
        return new ArrayList<>(store.values().stream()
                .filter(s -> strategyDefId.equals(s.getStrategyDefId()))
                .toList());
    }
}
