package com.riskplatform.engine.infrastructure.config;

import com.riskplatform.common.web.GlobalExceptionHandler;
import com.riskplatform.engine.application.DecisionMetrics;
import com.riskplatform.engine.application.TraceQueryService;
import com.riskplatform.engine.domain.decision.DecisionLogRepository;
import com.riskplatform.engine.infrastructure.metrics.MicrometerDecisionMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 可观测性组件装配（R15.2/R15.3/R15.4）。
 *
 * <p>装配：
 * <ul>
 *   <li>{@link DecisionMetrics} ← 基于 Micrometer 的实现（事件量/决策耗时 P50-P99/规则命中率），
 *       通过 {@code /actuator/prometheus} 暴露；</li>
 *   <li>{@link TraceQueryService} ← 执行链路查询服务（traceId 关联 eventId）。</li>
 * </ul>
 *
 * <p>Import 公共全局异常处理器，使链路查询的 NOT_FOUND 等返回统一结构化错误体。
 */
@Configuration
@Import(GlobalExceptionHandler.class)
public class ObservabilityConfig {

    @Bean
    public DecisionMetrics decisionMetrics(MeterRegistry meterRegistry) {
        return new MicrometerDecisionMetrics(meterRegistry);
    }

    @Bean
    public TraceQueryService traceQueryService(DecisionLogRepository decisionLogRepository) {
        return new TraceQueryService(decisionLogRepository);
    }
}
