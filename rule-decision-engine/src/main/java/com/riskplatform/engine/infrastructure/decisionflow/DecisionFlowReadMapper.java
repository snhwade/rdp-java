package com.riskplatform.engine.infrastructure.decisionflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** decision_flow 表只读 Mapper（子决策流节点在线加载子流程定义，R8.5）。 */
@Mapper
public interface DecisionFlowReadMapper extends BaseMapper<DecisionFlowReadPO> {
}
