package com.riskplatform.ruleconfig.infrastructure.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.strategy.StrategyCategory;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDef;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDefRepository;
import com.riskplatform.ruleconfig.domain.strategy.StrategyScope;
import com.riskplatform.ruleconfig.domain.strategy.StrategyStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * StrategyDef 仓储 MyBatis-Plus 实现（R3）。持久化到 strategy_def 表。
 */
@Repository
public class StrategyDefRepositoryImpl implements StrategyDefRepository {

    private final StrategyDefMapper mapper;

    public StrategyDefRepositoryImpl(StrategyDefMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public StrategyDef save(StrategyDef strategyDef) {
        StrategyDefPO po = toPO(strategyDef);
        mapper.insert(po);
        strategyDef.assignId(po.getId());
        return strategyDef;
    }

    @Override
    public void update(StrategyDef strategyDef) {
        mapper.updateById(toPO(strategyDef));
    }

    @Override
    public Optional<StrategyDef> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<StrategyDef> findByCode(String code) {
        StrategyDefPO po = mapper.selectOne(new LambdaQueryWrapper<StrategyDefPO>()
                .eq(StrategyDefPO::getCode, code));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return mapper.exists(new LambdaQueryWrapper<StrategyDefPO>().eq(StrategyDefPO::getCode, code));
    }

    @Override
    public boolean existsByCategoryAndCode(StrategyCategory category, String code) {
        return mapper.exists(new LambdaQueryWrapper<StrategyDefPO>()
                .eq(StrategyDefPO::getCategory, category.name())
                .eq(StrategyDefPO::getCode, code));
    }

    @Override
    public List<StrategyDef> findAll() {
        return mapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public List<StrategyDef> findByCategory(StrategyCategory category) {
        return mapper.selectList(new LambdaQueryWrapper<StrategyDefPO>()
                        .eq(StrategyDefPO::getCategory, category.name()))
                .stream().map(this::toDomain).toList();
    }

    // —— 内部辅助 ——

    private StrategyDefPO toPO(StrategyDef s) {
        StrategyDefPO po = new StrategyDefPO();
        po.setId(s.getId());
        po.setCategory(s.getCategory().name());
        po.setCode(s.getCode());
        po.setName(s.getName());
        po.setParamsJson(s.getParamsJson());
        po.setStatus(s.getStatus().name());
        po.setPriority(s.getPriority());
        StrategyScope scope = s.getScope();
        if (scope == null) {
            po.setAnyScope(false);
            po.setScopeScenarioId(null);
        } else {
            po.setAnyScope(scope.isAnyScope());
            po.setScopeScenarioId(scope.getScenarioId());
        }
        return po;
    }

    private StrategyDef toDomain(StrategyDefPO po) {
        StrategyStatus status = "DISABLED".equals(po.getStatus())
                ? StrategyStatus.DISABLED : StrategyStatus.ENABLED;
        StrategyCategory category = StrategyCategory.valueOf(po.getCategory());
        StrategyScope scope = resolveScope(category, po);
        return StrategyDef.rehydrate(po.getId(), category,
                po.getCode(), po.getName(), po.getParamsJson(), status, po.getPriority(), scope);
    }

    /**
     * 由持久化字段重建作用域。仅验证策略需要作用域：
     * any_scope=1 → 不限业务场景；否则按 scope_scenario_id 重建具体场景（缺失则为空）。
     */
    private StrategyScope resolveScope(StrategyCategory category, StrategyDefPO po) {
        if (category != StrategyCategory.VERIFY) {
            return null;
        }
        boolean anyScope = Boolean.TRUE.equals(po.getAnyScope());
        if (anyScope) {
            return StrategyScope.anyScenario();
        }
        if (po.getScopeScenarioId() == null) {
            return null;
        }
        return StrategyScope.rehydrate(false, po.getScopeScenarioId());
    }
}
