package com.riskplatform.ruleconfig.domain.indicator;

import java.util.List;
import java.util.Optional;

/**
 * 指标定义仓储端口（R7）。
 */
public interface IndicatorDefinitionRepository {

    IndicatorDefinition save(IndicatorDefinition definition);

    /** 更新已存在的指标定义（按 id）。 */
    IndicatorDefinition update(IndicatorDefinition definition);

    /** 按 id 删除指标定义，返回是否删除成功。 */
    boolean deleteById(Long id);

    Optional<IndicatorDefinition> findByRefName(String refName);

    Optional<IndicatorDefinition> findById(Long id);

    /** 列出指标定义，可按分组、事件与状态筛选。ungroupedOnly=true 时仅返回未分组指标。 */
    List<IndicatorDefinition> findAll(Long groupId, Boolean ungroupedOnly, String eventTypeCode, String status);

    boolean existsByRefName(String refName);

    /** 查询引用了指定指标 refName 的全部启用规则标识（R7.6）。 */
    List<Long> findReferencingRuleIds(String refName);
}
