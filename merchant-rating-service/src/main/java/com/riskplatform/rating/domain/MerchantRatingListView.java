package com.riskplatform.rating.domain;

import java.time.LocalDateTime;

/** 商户评级列表行视图（含更新时间，供 Admin 列表展示）。 */
public record MerchantRatingListView(
        String merchantId,
        Integer score,
        String level,
        String status,
        LocalDateTime updatedAt) {
}
