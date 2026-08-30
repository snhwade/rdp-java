package com.riskplatform.rating.infrastructure;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.riskplatform.common.web.GlobalExceptionHandler;
import com.riskplatform.rating.application.MerchantRatingService;
import com.riskplatform.rating.domain.MerchantRatingRepository;
import com.riskplatform.rating.domain.RatingScorer;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 商户评级服务装配（R12）。
 *
 * <p>评级因子权重通过配置注入；缺省给出一组示例权重，便于启动与联调。
 */
@Configuration
@Import(GlobalExceptionHandler.class)
public class RatingConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

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

    @Bean
    public MetaObjectHandler ratingMetaFillHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
