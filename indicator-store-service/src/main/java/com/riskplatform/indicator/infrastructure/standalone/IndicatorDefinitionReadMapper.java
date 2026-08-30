package com.riskplatform.indicator.infrastructure.standalone;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IndicatorDefinitionReadMapper {

    @Select("""
            SELECT ref_name AS refName, event_type_codes AS eventTypeCodes, dimensions,
                   window_days AS windowDays, slice_granularity AS sliceGranularity, acc_script AS accScript
            FROM indicator_definition
            WHERE status = 'ONLINE'
            """)
    List<IndicatorDefinitionRow> selectOnline();
}
