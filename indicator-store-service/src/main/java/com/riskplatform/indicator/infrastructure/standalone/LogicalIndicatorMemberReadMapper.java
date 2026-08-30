package com.riskplatform.indicator.infrastructure.standalone;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LogicalIndicatorMemberReadMapper {

    @Select("""
            SELECT member_ref_name AS memberRefName
            FROM logical_indicator_member
            WHERE logical_id = #{logicalId}
            ORDER BY sort_order
            """)
    List<LogicalIndicatorMemberRow> selectMemberRefs(long logicalId);
}
