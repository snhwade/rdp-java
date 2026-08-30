package com.riskplatform.ruleconfig.domain.strategy;

import java.util.List;

/**
 * 评分区间-策略绑定仓储端口（R3）。由基础设施层持久化到 score_band_strategy 表。
 */
public interface ScoreBandStrategyRepository {

    /** 保存新绑定，返回带 id 的实体。 */
    ScoreBandStrategy save(ScoreBandStrategy scoreBandStrategy);

    /** 全量替换某评分区间的策略绑定：先删后插。 */
    void replaceByScoreBandId(Long scoreBandId, List<ScoreBandStrategy> bindings);

    /** 按评分区间查询其策略绑定列表。 */
    List<ScoreBandStrategy> findByScoreBandId(Long scoreBandId);

    /** 按策略定义查询引用它的评分区间绑定列表（R5.8 关联关系查询）。 */
    List<ScoreBandStrategy> findByStrategyDefId(Long strategyDefId);
}
