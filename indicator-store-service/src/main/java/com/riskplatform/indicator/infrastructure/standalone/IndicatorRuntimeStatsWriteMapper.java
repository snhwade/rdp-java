package com.riskplatform.indicator.infrastructure.standalone;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 指标运行统计写入（IS1，与 rule-config 共享表）。 */
@Mapper
public interface IndicatorRuntimeStatsWriteMapper {

    @Insert("""
            INSERT INTO indicator_runtime_stats (ref_name, last_accumulate_at, read_miss_count)
            VALUES (#{refName}, NOW(3), 0)
            ON DUPLICATE KEY UPDATE last_accumulate_at = NOW(3), updated_at = NOW(3)
            """)
    void upsertAccumulate(@Param("refName") String refName);

    @Insert("""
            INSERT INTO indicator_runtime_stats (ref_name, read_miss_count)
            VALUES (#{refName}, 1)
            ON DUPLICATE KEY UPDATE read_miss_count = read_miss_count + 1, updated_at = NOW(3)
            """)
    void upsertReadMiss(@Param("refName") String refName);
}
