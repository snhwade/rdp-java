package com.riskplatform.engine.infrastructure.rulepackage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 评分区间-策略绑定只读持久化对象（对应 score_band_strategy 表，V16，扩展阶段 R6.2）。
 *
 * <p>在线决策面规则包评分模式据此把命中分值区间绑定的策略并入决策流累计结果。仅读取，不修改。
 */
@TableName("score_band_strategy")
public class ScoreBandStrategyReadPO {

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
