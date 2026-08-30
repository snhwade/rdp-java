package com.riskplatform.engine.domain.indicator;

/** 读取指标当前值（standalone 读 Redis，remote 调 indicator-store HTTP）。 */
public interface IndicatorReader {

    double read(String refName, String dimensionKey, int windowDays, String granularity);
}
