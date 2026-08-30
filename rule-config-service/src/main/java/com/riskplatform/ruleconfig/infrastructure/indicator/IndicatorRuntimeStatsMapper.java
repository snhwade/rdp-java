package com.riskplatform.ruleconfig.infrastructure.indicator;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 指标运行统计表（IS1）读写。 */
@Mapper
public interface IndicatorRuntimeStatsMapper {

    @Select("""
            <script>
            SELECT ref_name, last_accumulate_at, read_miss_count
            FROM indicator_runtime_stats
            WHERE ref_name IN
            <foreach collection="refNames" item="n" open="(" separator="," close=")">
              #{n}
            </foreach>
            </script>
            """)
    List<IndicatorRuntimeStatsRow> findByRefNames(@Param("refNames") List<String> refNames);

    @Select("""
            SELECT s.ref_name, s.last_accumulate_at, s.read_miss_count
            FROM indicator_runtime_stats s
            JOIN indicator_definition d ON d.ref_name = s.ref_name
            WHERE d.group_id = #{groupId}
            ORDER BY s.ref_name
            """)
    List<IndicatorRuntimeStatsRow> findByGroupId(@Param("groupId") Long groupId);

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
