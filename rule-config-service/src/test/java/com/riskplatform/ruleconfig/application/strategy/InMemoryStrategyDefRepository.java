package com.riskplatform.ruleconfig.application.strategy;

import com.riskplatform.ruleconfig.domain.strategy.StrategyCategory;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDef;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDefRepository;
import com.riskplatform.ruleconfig.domain.strategy.StrategyScope;
import com.riskplatform.ruleconfig.domain.strategy.StrategyStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版策略定义仓储，用于验证策略应用服务的单元测试。
 *
 * <p>关键点：唯一性查询 {@link #existsByCategoryAndCode} 以<strong>精确等值</strong>实现，
 * 模拟数据库唯一键 + 精确等值查询语义（R5.7），不做任何前缀/模糊匹配。
 * 保存时按持久化字段（priority/anyScope/scopeScenarioId）回放，往返读取等价于真实仓储。
 */
public class InMemoryStrategyDefRepository implements StrategyDefRepository {

    private final Map<Long, StrategyDef> store = new LinkedHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public StrategyDef save(StrategyDef strategyDef) {
        long id = seq.incrementAndGet();
        strategyDef.assignId(id);
        store.put(id, snapshot(strategyDef));
        return strategyDef;
    }

    @Override
    public void update(StrategyDef strategyDef) {
        store.put(strategyDef.getId(), snapshot(strategyDef));
    }

    @Override
    public Optional<StrategyDef> findById(Long id) {
        return Optional.ofNullable(store.get(id)).map(this::snapshot);
    }

    @Override
    public Optional<StrategyDef> findByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return store.values().stream().filter(s -> code.equals(s.getCode())).findFirst().map(this::snapshot);
    }

    @Override
    public boolean existsByCode(String code) {
        if (code == null) {
            return false;
        }
        return store.values().stream().anyMatch(s -> code.equals(s.getCode()));
    }

    @Override
    public boolean existsByCategoryAndCode(StrategyCategory category, String code) {
        if (category == null || code == null) {
            return false;
        }
        return store.values().stream()
                .anyMatch(s -> category == s.getCategory() && code.equals(s.getCode()));
    }

    @Override
    public List<StrategyDef> findAll() {
        return store.values().stream().map(this::snapshot).toList();
    }

    @Override
    public List<StrategyDef> findByCategory(StrategyCategory category) {
        return new ArrayList<>(store.values().stream()
                .filter(s -> s.getCategory() == category)
                .map(this::snapshot)
                .toList());
    }

    /** 复制一份策略定义（模拟持久化往返：经由 rehydrate 重建，不重复校验）。 */
    private StrategyDef snapshot(StrategyDef s) {
        StrategyScope scope = s.getScope();
        StrategyScope copiedScope = null;
        if (scope != null) {
            copiedScope = scope.isAnyScope()
                    ? StrategyScope.anyScenario()
                    : StrategyScope.rehydrate(false, scope.getScenarioId());
        }
        return StrategyDef.rehydrate(s.getId(), s.getCategory(), s.getCode(), s.getName(),
                s.getParamsJson(), s.getStatus() == null ? StrategyStatus.ENABLED : s.getStatus(),
                s.getPriority(), copiedScope);
    }
}
