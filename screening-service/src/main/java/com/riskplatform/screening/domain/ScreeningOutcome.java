package com.riskplatform.screening.domain;

/**
 * 筛查结果状态（R11.5/R11.6）：供决策引擎按处置策略消费。
 */
public enum ScreeningOutcome {
    /** 命中 */
    HIT,
    /** 未命中 */
    MISS,
    /** 超时（可配置时限内未返回，R11.5） */
    TIMEOUT,
    /** 失败（执行异常或名单数据不可用，R11.6） */
    FAILED
}
