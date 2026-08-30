package com.riskplatform.engine.infrastructure.decisiontool;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 决策工具只读 Mapper（运行时从配置库加载 refId 对应定义）。 */
public final class DecisionToolReadMappers {

    private DecisionToolReadMappers() {
    }

    @Mapper
    public interface DecisionTableReadMapper extends BaseMapper<DecisionTableReadPO> {
    }

    @Mapper
    public interface ScorecardReadMapper extends BaseMapper<ScorecardReadPO> {
    }

    @Mapper
    public interface DecisionTreeReadMapper extends BaseMapper<DecisionTreeReadPO> {
    }

    @Mapper
    public interface DecisionMatrixReadMapper extends BaseMapper<DecisionMatrixReadPO> {
    }
}
