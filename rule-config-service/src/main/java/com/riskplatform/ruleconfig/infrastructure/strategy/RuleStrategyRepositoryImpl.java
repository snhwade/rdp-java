package com.riskplatform.ruleconfig.infrastructure.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.strategy.RuleStrategy;
import com.riskplatform.ruleconfig.domain.strategy.RuleStrategyRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * RuleStrategy 仓储 MyBatis-Plus 实现（R3）。持久化到 rule_strategy 表。
 *
 * <p>「按规则全量替换」策略：替换时先删除该规则旧绑定再插入新绑定。
 */
@Repository
public class RuleStrategyRepositoryImpl implements RuleStrategyRepository {

    private final RuleStrategyMapper mapper;

    public RuleStrategyRepositoryImpl(RuleStrategyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RuleStrategy save(RuleStrategy ruleStrategy) {
        RuleStrategyPO po = toPO(ruleStrategy);
        mapper.insert(po);
        ruleStrategy.assignId(po.getId());
        return ruleStrategy;
    }

    @Override
    public void replaceByRuleV2Id(Long ruleV2Id, List<RuleStrategy> bindings) {
        mapper.delete(new LambdaQueryWrapper<RuleStrategyPO>()
                .eq(RuleStrategyPO::getRuleV2Id, ruleV2Id));
        for (RuleStrategy rs : bindings) {
            mapper.insert(toPO(rs));
        }
    }

    @Override
    public List<RuleStrategy> findByRuleV2Id(Long ruleV2Id) {
        return mapper.selectList(new LambdaQueryWrapper<RuleStrategyPO>()
                        .eq(RuleStrategyPO::getRuleV2Id, ruleV2Id))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<RuleStrategy> findByStrategyDefId(Long strategyDefId) {
        return mapper.selectList(new LambdaQueryWrapper<RuleStrategyPO>()
                        .eq(RuleStrategyPO::getStrategyDefId, strategyDefId))
                .stream().map(this::toDomain).toList();
    }

    // —— 内部辅助 ——

    private RuleStrategyPO toPO(RuleStrategy rs) {
        RuleStrategyPO po = new RuleStrategyPO();
        po.setId(rs.getId());
        po.setRuleV2Id(rs.getRuleV2Id());
        po.setStrategyDefId(rs.getStrategyDefId());
        po.setPriority(rs.getPriority());
        po.setExtraJson(rs.getExtraJson());
        return po;
    }

    private RuleStrategy toDomain(RuleStrategyPO po) {
        return RuleStrategy.rehydrate(po.getId(), po.getRuleV2Id(), po.getStrategyDefId(),
                po.getPriority(), po.getExtraJson());
    }
}
