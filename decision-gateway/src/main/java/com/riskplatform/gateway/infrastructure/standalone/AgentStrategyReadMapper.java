package com.riskplatform.gateway.infrastructure.standalone;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentStrategyReadMapper {

    @Select("""
            SELECT code, name, event_type_codes AS eventTypeCodes, config_json AS configJson,
                   status, adoption_mode AS adoptionMode
            FROM agent_strategy
            """)
    List<AgentStrategyRow> selectAll();
}
