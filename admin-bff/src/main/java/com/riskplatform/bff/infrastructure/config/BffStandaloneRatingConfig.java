package com.riskplatform.bff.infrastructure.config;

import com.riskplatform.rating.application.MerchantRatingService;
import com.riskplatform.rating.domain.MerchantRatingRepository;
import com.riskplatform.rating.domain.RatingScorer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * standalone BFF：装配商户评级服务，复用 rule-config 的 {@code MyBatisMetaFillHandler} 自动填充审计字段。
 */
@Configuration
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class BffStandaloneRatingConfig {

    @Bean
    public RatingScorer ratingScorer(@Value("${rating.weights.industry:30}") double industry,
                                     @Value("${rating.weights.region:30}") double region,
                                     @Value("${rating.weights.history:40}") double history) {
        return new RatingScorer(Map.of(
                "industry", industry,
                "region", region,
                "history", history));
    }

    @Bean
    public MerchantRatingService merchantRatingService(MerchantRatingRepository repository, RatingScorer scorer) {
        return new MerchantRatingService(repository, scorer);
    }
}
