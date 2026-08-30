package com.riskplatform.rating.application;

import com.riskplatform.common.error.ValidationException;
import com.riskplatform.common.model.PagedResult;
import com.riskplatform.rating.domain.IncompleteRatingDataException;
import com.riskplatform.rating.domain.MerchantRating;
import com.riskplatform.rating.domain.MerchantRatingListView;
import com.riskplatform.rating.domain.MerchantRatingQuery;
import com.riskplatform.rating.domain.MerchantRatingRepository;
import com.riskplatform.rating.domain.RatingScorer;

import java.util.Map;

/**
 * 商户评级应用服务（R12）。
 *
 * <p>触发评级计算（确定性评分 + 五档映射 + 持久化）；查询评级（未评级返回 UNRATED）；
 * 评级数据不完整时保留既有评级不变并抛出 {@link IncompleteRatingDataException}（R12.4）。
 */
public class MerchantRatingService {

    private final MerchantRatingRepository repository;
    private final RatingScorer scorer;

    public MerchantRatingService(MerchantRatingRepository repository, RatingScorer scorer) {
        this.repository = repository;
        this.scorer = scorer;
    }

    /** 触发评级计算并持久化最新结果（R12.1/R12.2/R12.3）。 */
    public MerchantRating computeAndSave(String merchantId, Map<String, Double> factorValues) {
        if (factorValues == null || factorValues.isEmpty()) {
            // R12.4：数据不完整，保留既有评级不变
            throw new IncompleteRatingDataException("评级因子缺失，保留既有评级");
        }
        int score = scorer.score(factorValues);
        MerchantRating rating = MerchantRating.rated(merchantId, score, factorValues);
        repository.save(rating);
        return rating;
    }

    /** 查询商户评级；未评级返回 UNRATED 状态（R12.5）。 */
    public MerchantRating query(String merchantId) {
        return repository.findByMerchantId(merchantId)
                .orElseGet(() -> MerchantRating.unrated(merchantId));
    }

    /** 分页列出商户评级（默认按更新时间降序，R12.7）。 */
    public PagedResult<MerchantRatingListView> list(MerchantRatingQuery query) {
        ValidationException.Builder errors = ValidationException.builder();
        if (query.isTimeRangeInverted()) {
            errors.field("timeRange", "起始时间不得晚于结束时间");
        }
        errors.throwIfAny();
        return repository.query(query);
    }
}
