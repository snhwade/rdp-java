package com.riskplatform.rating.domain;

/**
 * 商户评级列表分页查询条件（默认按 updated_at 降序）。
 */
public record MerchantRatingQuery(
        String merchantId,
        String status,
        String level,
        Long startTimeMs,
        Long endTimeMs,
        int page,
        int pageSize) {

    public MerchantRatingQuery {
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 200) {
            pageSize = 200;
        }
    }

    public boolean isTimeRangeInverted() {
        return startTimeMs != null && endTimeMs != null && startTimeMs > endTimeMs;
    }
}
