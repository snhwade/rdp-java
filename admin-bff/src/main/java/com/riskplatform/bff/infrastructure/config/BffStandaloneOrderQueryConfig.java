package com.riskplatform.bff.infrastructure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.riskplatform.common.crypto.FieldCryptoConfig;
import com.riskplatform.common.web.GlobalExceptionHandler;
import com.riskplatform.gateway.application.BusinessOrderQueryService;
import com.riskplatform.gateway.application.OrderQueryService;
import com.riskplatform.gateway.domain.OrderQueryStore;
import com.riskplatform.gateway.domain.OrderStore;
import com.riskplatform.gateway.infrastructure.order.MySqlOrderRepository;
import com.riskplatform.gateway.infrastructure.order.RiskOrderMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * standalone BFF：仅装配管理端所需的订单查询能力，不引入 {@code RiskEventService} 与内嵌引擎网关。
 */
@Configuration
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
@Import({GlobalExceptionHandler.class, FieldCryptoConfig.class})
public class BffStandaloneOrderQueryConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MySqlOrderRepository mySqlOrderRepository(RiskOrderMapper mapper) {
        return new MySqlOrderRepository(mapper);
    }

    @Bean
    public OrderStore orderStore(MySqlOrderRepository repo) {
        return repo;
    }

    @Bean
    public OrderQueryStore orderQueryStore(MySqlOrderRepository repo) {
        return repo;
    }

    @Bean
    public BusinessOrderQueryService businessOrderQueryService(MySqlOrderRepository repo) {
        return new BusinessOrderQueryService(repo);
    }

    @Bean
    public OrderQueryService orderQueryService(OrderQueryStore orderQueryStore) {
        return new OrderQueryService(orderQueryStore);
    }
}
