package com.riskplatform.ruleconfig.application.strategy;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.strategy.StrategyCategory;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDef;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDefRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 策略定义应用服务（R3.1/R3.2/R3.3/R3.4）。
 *
 * <p>负责四类策略（验证/状态管控/限额管控/通知/名单）定义的创建（code 唯一性校验）、
 * 更新、启停与查询编排。领域不变式（category/code/name 校验）在 {@link StrategyDef} 内完成。
 *
 * <p>通过 {@code @Service} 组件扫描自注册，避免改动共享装配类。
 */
@Service
public class StrategyDefAppService {

    private final StrategyDefRepository repository;

    public StrategyDefAppService(StrategyDefRepository repository) {
        this.repository = repository;
    }

    /** 创建策略定义（R3.1-3.4：四类策略共表，按 category 区分）。 */
    public StrategyDef create(StrategyCategory category, String code, String name, String paramsJson) {
        StrategyDef def = StrategyDef.create(category, code, name, paramsJson); // 校验 category/code/name
        if (repository.existsByCode(code)) {
            throw BizException.duplicate("策略 code 已存在: " + code);
        }
        return repository.save(def);
    }

    /** 更新策略定义名称与参数（R3）。 */
    public StrategyDef update(Long id, String name, String paramsJson) {
        StrategyDef def = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("策略不存在: " + id));
        def.update(name, paramsJson);
        repository.update(def);
        return def;
    }

    /** 设置启用/禁用状态。 */
    public StrategyDef setStatus(Long id, boolean enabled) {
        StrategyDef def = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("策略不存在: " + id));
        if (enabled) {
            def.enable();
        } else {
            def.disable();
        }
        repository.update(def);
        return def;
    }

    /** 按 id 查询。 */
    public StrategyDef get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("策略不存在: " + id));
    }

    /** 列表查询，可选按类别筛选。 */
    public List<StrategyDef> list(StrategyCategory category) {
        return category == null ? repository.findAll() : repository.findByCategory(category);
    }
}
