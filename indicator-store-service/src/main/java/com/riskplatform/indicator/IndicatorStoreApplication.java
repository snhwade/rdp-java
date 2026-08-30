package com.riskplatform.indicator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 指标存储服务启动入口。
 */
@SpringBootApplication
@MapperScan("com.riskplatform.indicator.infrastructure.standalone")
public class IndicatorStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(IndicatorStoreApplication.class, args);
    }
}
