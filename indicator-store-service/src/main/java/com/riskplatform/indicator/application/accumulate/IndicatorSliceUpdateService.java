package com.riskplatform.indicator.application.accumulate;

import com.riskplatform.common.model.IndicatorSliceUpdate;
import com.riskplatform.indicator.application.IndicatorStorageWriter;
import com.riskplatform.indicator.infrastructure.stats.IndicatorRuntimeStatsWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消费 Flink 回写的指标切片增量事件，按 {@code indicator.storage} 路由写入 Redis / ES。
 */
public class IndicatorSliceUpdateService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorSliceUpdateService.class);

    private final IndicatorStorageWriter storageWriter;
    private final IndicatorRuntimeStatsWriter runtimeStatsWriter;

    public IndicatorSliceUpdateService(IndicatorStorageWriter storageWriter,
                                       IndicatorRuntimeStatsWriter runtimeStatsWriter) {
        this.storageWriter = storageWriter;
        this.runtimeStatsWriter = runtimeStatsWriter;
    }

    public void apply(IndicatorSliceUpdate update) {
        if (update == null) {
            return;
        }
        try {
            storageWriter.applySliceIncrement(update);
            runtimeStatsWriter.recordAccumulate(update.refName());
            log.debug("切片增量已落库 {} += {}", update.sliceKey(), update.increment());
        } catch (RuntimeException e) {
            log.warn("切片增量落库失败: {} 原因={}", update.sliceKey(), e.getMessage());
        }
    }
}
