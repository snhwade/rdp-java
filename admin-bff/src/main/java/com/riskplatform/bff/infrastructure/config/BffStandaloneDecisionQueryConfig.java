package com.riskplatform.bff.infrastructure.config;

import com.riskplatform.gateway.adapter.decisionlog.AiDecisionRecordController;
import com.riskplatform.gateway.adapter.decisionlog.DecisionRecordController;
import com.riskplatform.gateway.adapter.decisionlog.EngineDecisionRecordController;
import com.riskplatform.gateway.application.DecisionExecutionLogService;
import com.riskplatform.gateway.application.InvocationTraceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * standalone BFF：显式注册嵌入式决策查询 REST 控制器（避免组件扫描遗漏）。
 */
@Configuration
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class BffStandaloneDecisionQueryConfig {

    @Bean
    public DecisionRecordController decisionRecordController(
            DecisionExecutionLogService service,
            InvocationTraceService traceService) {
        return new DecisionRecordController(service, traceService);
    }

    @Bean
    public EngineDecisionRecordController engineDecisionRecordController(
            DecisionExecutionLogService service,
            InvocationTraceService traceService) {
        return new EngineDecisionRecordController(service, traceService);
    }

    @Bean
    public AiDecisionRecordController aiDecisionRecordController(DecisionExecutionLogService service) {
        return new AiDecisionRecordController(service);
    }
}
