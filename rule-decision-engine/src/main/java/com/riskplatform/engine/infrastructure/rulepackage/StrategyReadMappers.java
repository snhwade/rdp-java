package com.riskplatform.engine.infrastructure.rulepackage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 规则包在线执行所需的策略只读 Mapper 集合（扩展阶段 R6.2）。
 *
 * <p>集中声明三张策略相关表的只读 Mapper，便于规则包节点在线加载策略绑定：
 * 策略定义、规则-策略绑定、评分区间-策略绑定。
 */
public final class StrategyReadMappers {

    private StrategyReadMappers() {
    }

    /** strategy_def 表只读 Mapper。 */
    @Mapper
    public interface StrategyDefReadMapper extends BaseMapper<StrategyDefReadPO> {
    }

    /** rule_strategy 表只读 Mapper。 */
    @Mapper
    public interface RuleStrategyReadMapper extends BaseMapper<RuleStrategyReadPO> {
    }

    /** score_band_strategy 表只读 Mapper。 */
    @Mapper
    public interface ScoreBandStrategyReadMapper extends BaseMapper<ScoreBandStrategyReadPO> {
    }
}
