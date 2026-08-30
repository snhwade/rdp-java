package com.riskplatform.gateway.agent.llm;

import com.riskplatform.gateway.agent.AgentRuntimeConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 构建 LLM 视图与已知风险清单。 */
public final class AgentLlmSupport {

    private AgentLlmSupport() {
    }

    public static LlmClientPort.AgentRuntimeConfigView toView(AgentRuntimeConfig config) {
        AgentRuntimeConfig.LlmConfig llm = config.llm == null ? new AgentRuntimeConfig.LlmConfig() : config.llm;
        return new LlmClientPort.AgentRuntimeConfigView(
                llm.systemPrompt,
                llm.model,
                llm.apiKeyEnv,
                null,
                llm.temperature > 0 ? llm.temperature : 0.2,
                false,
                1024,
                toKnownRiskMaps(config.knownRisks));
    }

    public static List<Map<String, Object>> toKnownRiskMaps(List<AgentRuntimeConfig.KnownRisk> risks) {
        if (risks == null || risks.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentRuntimeConfig.KnownRisk r : risks) {
            if (r == null || r.id == null) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.id);
            m.put("name", r.name);
            m.put("description", r.description);
            m.put("signalKeys", r.signalKeys);
            m.put("suggestTools", r.suggestTools);
            out.add(m);
        }
        return out;
    }

    public static String explorationInstructions() {
        return """
                工作方式（自主推理，非固定流程）：
                1) 根据 availableTools 与当前 toolTrace，自主决定下一步调用哪个工具、是否继续取证；
                2) 工具 output 是原始证据，禁止跳过 toolTrace 直接 finish；finish 时必须引用 toolTrace 中的具体字段/数值说明理由；
                3) knownRisks 仅为经验参考，可调用 check_known_risks 对照，但决策仍须你综合 toolTrace 自行推导；
                4) 在证据基础上可输出 unknownFindings（hypothesis + evidence + severity 1-5）；中高危 unknown 倾向 REVIEW/REJECT；
                5) 禁止使用 if-then 规则表或固定工具顺序替代推理。
                """;
    }
}
