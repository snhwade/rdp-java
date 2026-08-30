package com.riskplatform.rating.domain;

import java.util.Map;

/**
 * 商户评级聚合根（R12）。
 */
public class MerchantRating {

    private final String merchantId;
    private Integer score;
    private RiskLevel level;
    private RatingStatus status;
    private Map<String, Double> factors;

    private MerchantRating(String merchantId, Integer score, RiskLevel level,
                           RatingStatus status, Map<String, Double> factors) {
        this.merchantId = merchantId;
        this.score = score;
        this.level = level;
        this.status = status;
        this.factors = factors;
    }

    /** 未评级商户。 */
    public static MerchantRating unrated(String merchantId) {
        return new MerchantRating(merchantId, null, null, RatingStatus.UNRATED, null);
    }

    /** 已评级商户。 */
    public static MerchantRating rated(String merchantId, int score, Map<String, Double> factors) {
        RiskLevel level = RiskLevel.fromScore(score);
        return new MerchantRating(merchantId, score, level, RatingStatus.RATED, factors);
    }

    public String getMerchantId() {
        return merchantId;
    }

    public Integer getScore() {
        return score;
    }

    public RiskLevel getLevel() {
        return level;
    }

    public RatingStatus getStatus() {
        return status;
    }

    public Map<String, Double> getFactors() {
        return factors;
    }

    public boolean isRated() {
        return status == RatingStatus.RATED;
    }
}
