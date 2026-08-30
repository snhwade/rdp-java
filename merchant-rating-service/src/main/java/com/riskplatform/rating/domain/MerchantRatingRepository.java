package com.riskplatform.rating.domain;

import com.riskplatform.common.model.PagedResult;

import java.util.Optional;

/**
 * 商户评级仓储端口（R12.3/R12.5）。由基础设施层用 MyBatis-Plus 持久化到 merchant_rating 表。
 */
public interface MerchantRatingRepository {

    /** 保存/更新商户最新评级。 */
    void save(MerchantRating rating);

    /** 查询商户评级（不存在表示未评级）。 */
    Optional<MerchantRating> findByMerchantId(String merchantId);

    /** 分页查询商户评级（按 updated_at 降序）。 */
    PagedResult<MerchantRatingListView> query(MerchantRatingQuery query);
}
