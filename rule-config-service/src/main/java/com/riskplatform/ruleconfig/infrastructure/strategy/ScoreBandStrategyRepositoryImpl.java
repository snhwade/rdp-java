package com.riskplatform.ruleconfig.infrastructure.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.strategy.ScoreBandStrategy;
import com.riskplatform.ruleconfig.domain.strategy.ScoreBandStrategyRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ScoreBandStrategy 仓储 MyBatis-Plus 实现（R3）。持久化到 score_band_strategy 表。
 *
 * <p>「按评分区间全量替换」策略：替换时先删除该区间旧绑定再插入新绑定。
 */
@Repository
public class ScoreBandStrategyRepositoryImpl implements ScoreBandStrategyRepository {

    private final ScoreBandStrategyMapper mapper;

    public ScoreBandStrategyRepositoryImpl(ScoreBandStrategyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ScoreBandStrategy save(ScoreBandStrategy scoreBandStrategy) {
        ScoreBandStrategyPO po = toPO(scoreBandStrategy);
        mapper.insert(po);
        scoreBandStrategy.assignId(po.getId());
        return scoreBandStrategy;
    }

    @Override
    public void replaceByScoreBandId(Long scoreBandId, List<ScoreBandStrategy> bindings) {
        mapper.delete(new LambdaQueryWrapper<ScoreBandStrategyPO>()
                .eq(ScoreBandStrategyPO::getScoreBandId, scoreBandId));
        for (ScoreBandStrategy s : bindings) {
            mapper.insert(toPO(s));
        }
    }

    @Override
    public List<ScoreBandStrategy> findByScoreBandId(Long scoreBandId) {
        return mapper.selectList(new LambdaQueryWrapper<ScoreBandStrategyPO>()
                        .eq(ScoreBandStrategyPO::getScoreBandId, scoreBandId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<ScoreBandStrategy> findByStrategyDefId(Long strategyDefId) {
        return mapper.selectList(new LambdaQueryWrapper<ScoreBandStrategyPO>()
                        .eq(ScoreBandStrategyPO::getStrategyDefId, strategyDefId))
                .stream().map(this::toDomain).toList();
    }

    // —— 内部辅助 ——

    private ScoreBandStrategyPO toPO(ScoreBandStrategy s) {
        ScoreBandStrategyPO po = new ScoreBandStrategyPO();
        po.setId(s.getId());
        po.setScoreBandId(s.getScoreBandId());
        po.setStrategyDefId(s.getStrategyDefId());
        return po;
    }

    private ScoreBandStrategy toDomain(ScoreBandStrategyPO po) {
        return ScoreBandStrategy.rehydrate(po.getId(), po.getScoreBandId(), po.getStrategyDefId());
    }
}
