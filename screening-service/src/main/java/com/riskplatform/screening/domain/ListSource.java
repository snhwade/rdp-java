package com.riskplatform.screening.domain;

/**
 * 名单来源（R11.1/R11.2）。
 */
public enum ListSource {
    /** 普通名单 */
    WATCHLIST,
    /** 制裁名单 */
    SANCTION,
    /** 道琼斯类名单 */
    DOW_JONES
}
