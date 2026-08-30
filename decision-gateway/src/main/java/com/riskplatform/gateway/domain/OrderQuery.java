package com.riskplatform.gateway.domain;

/**
 * 订单查询条件（R10.4/R10.5）。
 *
 * <p>三类过滤条件（商户/事件类型/时间范围）均可选，但调用方至少需提供其一；
 * 起始时间与结束时间需成对语义校验（起始不得晚于结束）。校验由应用服务负责，
 * 本值对象仅承载条件与分页参数。
 *
 * @param merchantId    商户标识过滤（可空）
 * @param eventTypeCode 事件类型 code 过滤（可空）
 * @param startTimeMs   事件时间范围起始（毫秒时间戳，可空）
 * @param endTimeMs     事件时间范围结束（毫秒时间戳，可空）
 * @param page          页码（从 1 开始）
 * @param pageSize      每页大小（1–200）
 */
public record OrderQuery(
        String merchantId,
        String eventTypeCode,
        Long startTimeMs,
        Long endTimeMs,
        int page,
        int pageSize) {

    /** 每页最大条数（R10.4）。 */
    public static final int MAX_PAGE_SIZE = 200;

    /** 是否提供了至少一个过滤条件（商户/事件类型/时间范围任一）。 */
    public boolean hasAnyFilter() {
        return notBlank(merchantId)
                || notBlank(eventTypeCode)
                || startTimeMs != null
                || endTimeMs != null;
    }

    /** 起始时间是否晚于结束时间（两者均提供时才判定）。 */
    public boolean isTimeRangeInverted() {
        return startTimeMs != null && endTimeMs != null && startTimeMs > endTimeMs;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
