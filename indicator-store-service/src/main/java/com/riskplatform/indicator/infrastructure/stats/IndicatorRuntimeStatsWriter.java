package com.riskplatform.indicator.infrastructure.stats;

import com.riskplatform.indicator.infrastructure.standalone.IndicatorRuntimeStatsWriteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 指标运行统计写入（IS1）：累计成功 / 读缺失时更新共享 MySQL 表。
 */
@Component
public class IndicatorRuntimeStatsWriter {

    private static final Logger log = LoggerFactory.getLogger(IndicatorRuntimeStatsWriter.class);

    private final IndicatorRuntimeStatsWriteMapper statsMapper;

    public IndicatorRuntimeStatsWriter(IndicatorRuntimeStatsWriteMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    public void recordAccumulate(String refName) {
        if (refName == null || refName.isBlank()) {
            return;
        }
        try {
            statsMapper.upsertAccumulate(refName);
        } catch (DataAccessException ex) {
            log.debug("记录累计统计失败: refName={} {}", refName, ex.getMessage());
        }
    }

    public void recordReadMiss(String refName) {
        if (refName == null || refName.isBlank()) {
            return;
        }
        try {
            statsMapper.upsertReadMiss(refName);
        } catch (DataAccessException ex) {
            log.debug("记录读缺失统计失败: refName={} {}", refName, ex.getMessage());
        }
    }
}
