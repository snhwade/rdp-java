package com.riskplatform.indicator.domain;

/**
 * 指标读取结果（R9.3/R9.4/R16.3）。
 *
 * @param value      指标值
 * @param source     数据来源（REDIS/ES/DEFAULT）
 * @param missing    是否发生指标缺失（两源均不可读，取默认值时为 true，需记录缺失）
 */
public record IndicatorReadResult(double value, Source source, boolean missing) {

    public enum Source { REDIS, ES, DEFAULT, VIRTUAL }

    public static IndicatorReadResult virtual(double v) {
        return new IndicatorReadResult(v, Source.VIRTUAL, false);
    }

    public static IndicatorReadResult fromRedis(double v) {
        return new IndicatorReadResult(v, Source.REDIS, false);
    }

    public static IndicatorReadResult fromEs(double v) {
        return new IndicatorReadResult(v, Source.ES, false);
    }

    public static IndicatorReadResult defaultValue(double v) {
        return new IndicatorReadResult(v, Source.DEFAULT, true);
    }
}
