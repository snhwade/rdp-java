package com.riskplatform.gateway.agent;

import com.riskplatform.gateway.agent.llm.AgentLlmConfigurer;
import com.riskplatform.gateway.agent.llm.LlmClientPort;
import com.riskplatform.gateway.domain.AiAdviseResult;
import com.riskplatform.gateway.domain.AiLlmUnavailableException;
import com.riskplatform.gateway.infrastructure.config.AgentLlmProperties;
import com.riskplatform.gateway.infrastructure.metrics.AiAdviseMetrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 自主编排：每步由大模型选择工具或 finish。
 */
public class AgentOrchestrator {

    private final AgentToolRegistry toolRegistry;
    private final LlmClientPort llmClient;
    private final AgentLlmConfigurer llmConfigurer;
    private final AgentLlmProperties properties;
    private final AiAdviseMetrics metrics;

    public AgentOrchestrator(
            AgentToolRegistry toolRegistry,
            LlmClientPort llmClient,
            AgentLlmConfigurer llmConfigurer,
            AgentLlmProperties properties,
            AiAdviseMetrics metrics) {
        this.toolRegistry = toolRegistry;
        this.llmClient = llmClient;
        this.llmConfigurer = llmConfigurer;
        this.properties = properties;
        this.metrics = metrics;
    }

    public AiAdviseResult run(AgentRuntimeConfig config, AgentToolContext ctx) {
        List<Map<String, Object>> catalog = toolRegistry.buildToolCatalog(config);
        int maxSteps = config.maxOrchestrationSteps <= 0 ? 6 : config.maxOrchestrationSteps;

        for (int step = 0; step < maxSteps; step++) {
            LlmClientPort.OrchestrationStep orchStep = llmClient.orchestrateStep(
                    llmConfigurer.toLlmView(config),
                    ctx.eventTypeCode(),
                    ctx.engineDecision(),
                    catalog,
                    ctx.trace(),
                    ctx.signals(),
                    step);

            if (orchStep == null) {
                break;
            }

            if (orchStep.isFinish()) {
                Map<String, Object> finishOut = new HashMap<>();
                finishOut.put("decision", orchStep.decision());
                finishOut.put("confidence", orchStep.confidence());
                finishOut.put("reason", orchStep.reason());
                finishOut.put("knownHits", orchStep.knownHits());
                finishOut.put("unknownFindings", orchStep.unknownFindings());
                ctx.addTrace("orchestrator_finish", finishOut);
                String reason = buildReasonWithUnknown(orchStep.reason(), orchStep.unknownFindings());
                return new AiAdviseResult(
                        orchStep.decision(),
                        orchStep.confidence(),
                        reason,
                        ctx.trace());
            }

            if (orchStep.tool() != null) {
                toolRegistry.invokeTool(orchStep.tool(), orchStep.args(), ctx, config);
            }
        }

        return synthesizeFromCollectedEvidence(config, ctx);
    }

    /** 步数用尽或 LLM 单步失败时，基于已收集的 toolTrace 做一次综合推理。 */
    private AiAdviseResult synthesizeFromCollectedEvidence(AgentRuntimeConfig config, AgentToolContext ctx) {
        AiAdviseResult synthesized = llmClient.advise(
                llmConfigurer.toLlmView(config),
                ctx.eventTypeCode(),
                ctx.engineDecision(),
                ctx.trace(),
                ctx.signals());
        if (synthesized != null) {
            return synthesized;
        }
        return onLlmUnavailable(config, ctx, "LLM 不可用，无法基于工具数据完成推理");
    }

    private AiAdviseResult onLlmUnavailable(AgentRuntimeConfig config, AgentToolContext ctx, String reason) {
        if (metrics != null) {
            metrics.llmUnavailable();
        }
        boolean allowFallback = properties == null
                || properties.getOrchestration() == null
                || properties.getOrchestration().isAllowHeuristicFallback();
        ctx.addTrace("llm_unavailable", Map.of(
                "allowHeuristicFallback", allowFallback,
                "reason", reason));
        if (!allowFallback) {
            throw new AiLlmUnavailableException(reason);
        }
        if (metrics != null) {
            metrics.heuristicFallback();
        }
        String decision = config.defaultDecision == null ? "PASS" : config.defaultDecision;
        return new AiAdviseResult(
                decision,
                config.defaultConfidence,
                reason + "（已启用启发式回退）",
                ctx.trace());
    }

    private static String buildReasonWithUnknown(String reason, List<Map<String, Object>> unknownFindings) {
        if (unknownFindings == null || unknownFindings.isEmpty()) {
            return reason;
        }
        return reason + "；发现 " + unknownFindings.size() + " 条未知风险假设（见 trace.unknownFindings）";
    }
}
