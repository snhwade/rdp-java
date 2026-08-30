package com.riskplatform.gateway.domain;

/**
 * 指标读取（Agent 工具用）。
 */
public interface IndicatorReadGateway {

    double read(String refName, String dimensionKey, int windowDays, String granularity);
}
