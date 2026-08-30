package com.riskplatform.gateway.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.gateway.agent.llm.AgentLlmConfigurer;
import com.riskplatform.gateway.agent.llm.LlmClientPort;
import com.riskplatform.gateway.domain.AiAdviseResult;
import com.riskplatform.gateway.domain.AiLlmUnavailableException;
import com.riskplatform.gateway.infrastructure.config.AgentLlmProperties;
import com.riskplatform.gateway.infrastructure.metrics.AiAdviseMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentOrchestratorFallbackTest {

    @Test
    void llmUnavailable_withFallbackDisabled_throws() {
        AgentLlmProperties props = new AgentLlmProperties();
        props.getOrchestration().setAllowHeuristicFallback(false);
        AgentOrchestrator orch = orchestrator(props, null);

        AgentRuntimeConfig config = new AgentRuntimeConfig();
        config.llmMode = "ORCHESTRATED";
        config.maxOrchestrationSteps = 1;
        AgentToolContext ctx = new AgentToolContext("e1", "EVT", Map.of(), "PASS", new ArrayList<>());

        assertThatThrownBy(() -> orch.run(config, ctx))
                .isInstanceOf(AiLlmUnavailableException.class);
    }

    @Test
    void llmUnavailable_withFallbackEnabled_returnsDefault() {
        AgentLlmProperties props = new AgentLlmProperties();
        props.getOrchestration().setAllowHeuristicFallback(true);
        AgentOrchestrator orch = orchestrator(props, null);

        AgentRuntimeConfig config = new AgentRuntimeConfig();
        config.llmMode = "ORCHESTRATED";
        config.maxOrchestrationSteps = 1;
        config.defaultDecision = "REVIEW";
        config.defaultConfidence = 0.4;
        AgentToolContext ctx = new AgentToolContext("e1", "EVT", Map.of(), "PASS", new ArrayList<>());

        AiAdviseResult result = orch.run(config, ctx);
        assertThat(result.decision()).isEqualTo("REVIEW");
        assertThat(result.reason()).contains("启发式回退");
    }

    private static AgentOrchestrator orchestrator(AgentLlmProperties props, LlmClientPort llm) {
        LlmClientPort client = llm != null ? llm : new LlmClientPort() {
            @Override
            public AiAdviseResult advise(AgentRuntimeConfigView config, String eventTypeCode,
                                         String engineDecision, List<Map<String, Object>> trace,
                                         Map<String, Object> signals) {
                return null;
            }

            @Override
            public OrchestrationStep orchestrateStep(AgentRuntimeConfigView config, String eventTypeCode,
                                                     String engineDecision, List<Map<String, Object>> toolCatalog,
                                                     List<Map<String, Object>> trace, Map<String, Object> signals,
                                                     int stepIndex) {
                return null;
            }
        };
        AgentToolRegistry registry = new AgentToolRegistry(
                context -> com.riskplatform.gateway.domain.ListGateway.ListCheckSummary.empty(),
                name -> com.riskplatform.gateway.domain.ScreeningGateway.HitKind.NONE,
                (ref, dim, ts, gran) -> 0.0);
        return new AgentOrchestrator(
                registry,
                client,
                new AgentLlmConfigurer(props),
                props,
                new AiAdviseMetrics(new SimpleMeterRegistry()));
    }
}
