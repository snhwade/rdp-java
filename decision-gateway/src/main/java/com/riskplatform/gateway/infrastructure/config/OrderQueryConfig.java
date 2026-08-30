package com.riskplatform.gateway.infrastructure.config;

import com.riskplatform.common.web.GlobalExceptionHandler;
import com.riskplatform.common.crypto.FieldCryptoConfig;
import com.riskplatform.gateway.application.BusinessOrderQueryService;
import com.riskplatform.gateway.application.DecisionExecutionLogService;
import com.riskplatform.gateway.application.OrderQueryService;
import com.riskplatform.gateway.domain.EngineGateway;
import com.riskplatform.gateway.domain.EventIdGenerator;
import com.riskplatform.gateway.domain.OrderQueryStore;
import com.riskplatform.gateway.domain.OrderStore;
import com.riskplatform.gateway.domain.RiskEventValidator;
import com.riskplatform.gateway.infrastructure.client.RestEngineGateway;
import com.riskplatform.gateway.infrastructure.client.RestEventTypeStatusChecker;
import com.riskplatform.gateway.infrastructure.client.RestListGateway;
import com.riskplatform.gateway.infrastructure.client.RestScreeningGateway;
import com.riskplatform.gateway.infrastructure.config.AgentLlmProperties;
import com.riskplatform.gateway.infrastructure.order.MySqlOrderRepository;
import com.riskplatform.gateway.application.RiskEventService;
import com.riskplatform.gateway.infrastructure.order.RiskOrderMapper;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Configuration
@Import({GlobalExceptionHandler.class, FieldCryptoConfig.class, EmbeddedEngineConfiguration.class})
public class OrderQueryConfig {

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

    @Bean
    public EventIdGenerator eventIdGenerator() {
        return () -> "evt-" + UUID.randomUUID().toString().replace("-", "");
    }

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public RiskEventValidator.EventTypeStatusChecker remoteEventTypeStatusChecker(
            @Value("${downstream.rule-config:http://localhost:8082}") String ruleConfigBaseUrl) {
        return new RestEventTypeStatusChecker(RestClient.create(), ruleConfigBaseUrl);
    }

    @Bean
    public RiskEventValidator riskEventValidator(RiskEventValidator.EventTypeStatusChecker checker) {
        return new RiskEventValidator(checker);
    }

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public EngineGateway remoteEngineGateway(
            @Value("${downstream.rule-decision-engine:http://localhost:8083}") String engineBaseUrl) {
        return new RestEngineGateway(RestClient.create(), engineBaseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public com.riskplatform.gateway.domain.ScreeningGateway remoteScreeningGateway(
            @Value("${downstream.screening:http://localhost:8085}") String screeningBaseUrl) {
        return new RestScreeningGateway(RestClient.create(), screeningBaseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public com.riskplatform.gateway.domain.ListGateway remoteListGateway(
            @Value("${downstream.screening:http://localhost:8085}") String screeningBaseUrl) {
        return new RestListGateway(RestClient.create(), screeningBaseUrl);
    }

    @Bean
    public RiskEventService riskEventService(RiskEventValidator validator,
                                             EventIdGenerator eventIdGenerator,
                                             EngineGateway engineGateway,
                                             OrderStore orderStore,
                                             com.riskplatform.gateway.domain.ListGateway listGateway,
                                             com.riskplatform.gateway.domain.ScreeningGateway screeningGateway,
                                             DecisionExecutionLogService decisionExecutionLogService,
                                             AgentLlmProperties agentLlmProperties,
                                             com.riskplatform.gateway.domain.AgentStrategyPort agentStrategyPort) {
        return new RiskEventService(validator, eventIdGenerator, engineGateway, orderStore,
                listGateway, screeningGateway, decisionExecutionLogService, agentLlmProperties, agentStrategyPort);
    }
}
