package com.riskplatform.ruleconfig.domain.strategy;

import java.util.List;
import java.util.Optional;

/**
 * 策略定义仓储端口（R3）。由基础设施层用 MyBatis-Plus 持久化到 strategy_def 表。
 */
public interface StrategyDefRepository {

    /** 保存新策略定义，返回带 id 的实体。 */
    StrategyDef save(StrategyDef strategyDef);

    /** 更新策略定义（名称/参数/状态）。 */
    void update(StrategyDef strategyDef);

    /** 按 id 查询。 */
    Optional<StrategyDef> findById(Long id);

    /** 按 code 查询。 */
    Optional<StrategyDef> findByCode(String code);

    /** code 是否已存在。 */
    boolean existsByCode(String code);

    /** 在指定类别内 code 是否已存在（精确等值；R5.7 验证策略 code 唯一）。 */
    boolean existsByCategoryAndCode(StrategyCategory category, String code);

    /** 查询全部策略定义（无则空列表）。 */
    List<StrategyDef> findAll();

    /** 按类别筛选策略定义。 */
    List<StrategyDef> findByCategory(StrategyCategory category);
}
