package com.riskplatform.indicator.integration.support;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 指标存储集成测试断言/清理（仅 test scope）。 */
@Mapper
public interface IndicatorStoreIntegrationTestDataMapper {

    @Select("SELECT read_miss_count FROM indicator_runtime_stats WHERE ref_name = #{refName}")
    Long findReadMissCount(@Param("refName") String refName);

    @Delete("DELETE FROM indicator_runtime_stats WHERE ref_name LIKE #{pattern}")
    void deleteRuntimeStatsByRefNamePattern(@Param("pattern") String pattern);
}
