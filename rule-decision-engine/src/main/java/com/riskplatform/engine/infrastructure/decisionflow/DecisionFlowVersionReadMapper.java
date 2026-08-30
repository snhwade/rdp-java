package com.riskplatform.engine.infrastructure.decisionflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** decision_flow_version 只读 Mapper。 */
@Mapper
public interface DecisionFlowVersionReadMapper extends BaseMapper<DecisionFlowVersionReadPO> {

    @Select("""
            SELECT id, decision_flow_id, version, snapshot_json, status
            FROM decision_flow_version
            WHERE decision_flow_id = #{flowId} AND status = 'ONLINE'
            ORDER BY version DESC
            LIMIT 1
            """)
    DecisionFlowVersionReadPO selectOnlineByFlowId(long flowId);
}
