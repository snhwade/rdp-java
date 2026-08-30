package com.riskplatform.rating;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商户评级服务启动入口。
 *
 * <p>限界上下文：商户风险评级。负责评分计算（确定性）、五档等级映射、评级持久化与供规则引用。
 */
@SpringBootApplication
@MapperScan("com.riskplatform.rating.infrastructure")
public class MerchantRatingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantRatingApplication.class, args);
    }
}
