package com.riskplatform.engine.infrastructure.config;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.riskplatform.engine.application.DecisionLogService;
import com.riskplatform.engine.application.IndicatorContextEnricher;
import com.riskplatform.engine.domain.decision.DecisionLogRepository;
import com.riskplatform.engine.domain.indicator.IndicatorReader;
import com.riskplatform.engine.domain.rule.FailureRecorder;
import com.riskplatform.engine.domain.rule.RuleExecutor;
import com.riskplatform.engine.domain.rule.RuleExpressionEvaluator;
import com.riskplatform.engine.infrastructure.client.IndicatorStoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 规则执行内核与指标注入装配（R5/R15.1）。
 *
 * <p>供规则包、决策流等 V2 执行路径复用 {@link RuleExecutor} 与 {@link IndicatorContextEnricher}。
 */
@Configuration
public class DecisionOrchestrationConfig {

    private static final Logger log = LoggerFactory.getLogger(DecisionOrchestrationConfig.class);

    @Bean
    public RuleExpressionEvaluator ruleExpressionEvaluator() {
        AviatorEvaluatorInstance aviator = AviatorEvaluator.newInstance();
        return (expression, context) -> {
            Object result = aviator.execute(expression, context == null ? Map.of() : context, true);
            return Boolean.TRUE.equals(result);
        };
    }

    @Bean
    public FailureRecorder failureRecorder() {
        return (rule, cause) -> log.warn("规则求值失败 ruleId={} 原因={}",
                rule.ruleId(), cause == null ? "?" : cause.getMessage());
    }

    @Bean
    public RuleExecutor ruleExecutor(RuleExpressionEvaluator evaluator, FailureRecorder failureRecorder) {
        return new RuleExecutor(evaluator, failureRecorder);
    }

    @Bean
    public DecisionLogService decisionLogService(DecisionLogRepository repository) {
        return new DecisionLogService(repository);
    }

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public IndicatorStoreClient remoteIndicatorReader(
            @Value("${downstream.indicator-store:http://localhost:8084}") String indicatorStoreBaseUrl) {
        return new IndicatorStoreClient(RestClient.create(), indicatorStoreBaseUrl);
    }

    @Bean
    public IndicatorContextEnricher indicatorContextEnricher(IndicatorReader indicatorReader) {
        return new IndicatorContextEnricher(indicatorReader);
    }
}
