package com.riskplatform.ruleconfig.infrastructure.indicator;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IndicatorGroupMapper extends BaseMapper<IndicatorGroupPO> {

    @Select("SELECT COUNT(*) FROM indicator_definition WHERE group_id = #{groupId}")
    long countAllIndicatorsByGroup(@Param("groupId") Long groupId);

    @Select("""
            SELECT COUNT(*) FROM indicator_definition
            WHERE group_id = #{groupId}
              AND (#{status} IS NULL OR status = #{status})
            """)
    long countIndicatorsByGroup(@Param("groupId") Long groupId, @Param("status") String status);

    @Select("""
            SELECT COUNT(*) FROM indicator_definition
            WHERE group_id IS NULL
              AND (#{status} IS NULL OR status = #{status})
            """)
    long countUngroupedIndicators(@Param("status") String status);
}
