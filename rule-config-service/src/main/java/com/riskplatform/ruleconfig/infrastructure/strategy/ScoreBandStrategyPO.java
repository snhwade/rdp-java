package com.riskplatform.ruleconfig.infrastructure.strategy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * score_band_strategy 表持久化对象（V16）。评分区间-策略绑定。
 */
@TableName("score_band_strategy")
public class ScoreBandStrategyPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scoreBandId;
    private Long strategyDefId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getScoreBandId() {
        return scoreBandId;
    }

    public void setScoreBandId(Long scoreBandId) {
        this.scoreBandId = scoreBandId;
    }

    public Long getStrategyDefId() {
        return strategyDefId;
    }

    public void setStrategyDefId(Long strategyDefId) {
        this.strategyDefId = strategyDefId;
    }
}
