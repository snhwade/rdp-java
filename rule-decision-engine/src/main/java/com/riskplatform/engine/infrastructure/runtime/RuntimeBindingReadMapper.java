package com.riskplatform.engine.infrastructure.runtime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 运行时按事件解析规则包/决策流绑定（只读）。 */
@Mapper
public interface RuntimeBindingReadMapper {

    @Select("""
            SELECT rule_package_id
            FROM rule_package_event
            WHERE event_type_code = #{eventTypeCode}
            ORDER BY rule_package_id
            """)
    List<Long> selectRulePackageIdsByEvent(String eventTypeCode);

    @Select("""
            SELECT id
            FROM decision_flow
            WHERE event_type_code = #{eventTypeCode} AND status = 'ENABLED'
            ORDER BY id
            LIMIT 1
            """)
    Long selectEnabledFlowIdByEvent(String eventTypeCode);
}
