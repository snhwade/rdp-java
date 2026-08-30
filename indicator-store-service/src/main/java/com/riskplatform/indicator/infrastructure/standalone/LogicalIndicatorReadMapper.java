package com.riskplatform.indicator.infrastructure.standalone;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LogicalIndicatorReadMapper {

    @Select("""
            SELECT id, ref_name AS refName, combine_mode AS combineMode, combine_expression AS combineExpression
            FROM logical_indicator
            WHERE status = 'ONLINE'
            """)
    List<LogicalIndicatorRow> selectOnline();
}
