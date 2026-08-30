package com.riskplatform.indicator.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * ES 指标存储端口（R9.1/R9.4/R9.5）。由基础设施层用 Elasticsearch Java Client 实现。
 *
 * <p>不可用时实现应抛出 {@link EsUnavailableException}，由读路由触发回退/不可读处理。
 */
public interface EsStore {

    /** 写入指标切片文档。 */
    void write(String refName, String dimensionKey, long sliceTs, double value, String orderId);

    /** 读取单个切片当前值（用于增量累加写入）。 */
    Optional<Double> readSlice(String refName, String dimensionKey, long sliceTs);

    /** 按维度与窗口范围聚合读取指标当前值。 */
    Optional<Double> readWindow(String refName, String dimensionKey, int windowDays,
                                SliceGranularity granularity, Instant now);

    /** ES 不可用异常。 */
    class EsUnavailableException extends RuntimeException {
        public EsUnavailableException(String message) {
            super(message);
        }
    }
}
