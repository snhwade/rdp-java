package com.riskplatform.screening;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 筛查服务启动入口。
 *
 * <p>限界上下文：名单筛查。负责名单/制裁/道琼斯名称筛查、相似度阈值匹配与超时/失败处置信号，
 * 以及名单记录管理（S1：黑/白/关注名单 CRUD 与命中判定）。
 */
@SpringBootApplication
@MapperScan("com.riskplatform.screening.infrastructure")
public class ScreeningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScreeningServiceApplication.class, args);
    }
}
