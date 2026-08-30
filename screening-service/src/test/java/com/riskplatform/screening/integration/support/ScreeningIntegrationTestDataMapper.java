package com.riskplatform.screening.integration.support;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 筛查服务集成测试清理（仅 test scope）。 */
@Mapper
public interface ScreeningIntegrationTestDataMapper {

    @Delete("DELETE FROM list_record WHERE reason LIKE #{pattern}")
    void deleteListRecordsByReasonPattern(@Param("pattern") String pattern);
}
