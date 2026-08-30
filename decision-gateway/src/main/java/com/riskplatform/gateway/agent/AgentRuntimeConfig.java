package com.riskplatform.gateway.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentRuntimeConfig {

  /** 未配置时由网关 application.yml 回落为 ORCHESTRATED。 */
    public String llmMode;
    public LlmConfig llm;
    public List<ToolConfig> tools = new ArrayList<>();
    public List<RuleConfig> rules = new ArrayList<>();
    public String defaultDecision = "PASS";
    public double defaultConfidence = 0.75;
    public String defaultReason = "上下文无显著风险信号";
    /** 自主编排最大步数（ORCHESTRATED 模式）。 */
    public int maxOrchestrationSteps = 6;
    /** 提供给 Agent 的重点特征字段名（read_context 会摘要展示）。 */
    public List<String> featureFields = new ArrayList<>();
    /** 经验沉淀的已知风险点（Agent 先覆盖，再推导未知风险）。 */
    public List<KnownRisk> knownRisks = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LlmConfig {
        /** 未配置时由网关 application.yml 回落（默认 deepseek）。 */
        public String provider;
        public String model;
        public String apiKeyEnv;
        public String systemPrompt;
        public double temperature = 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolConfig {
        public String id;
        public boolean enabled = true;
        public List<String> fields;
        public List<IndicatorRef> refs;
        /** analyze_amount_spike：日累计指标 refName。 */
        public String dailyAmtRef = "b2b_daily_amt";
        public String amountField = "amount";
        public String countRef = "txn_cnt_1d";
        /** 单笔金额超过日均的倍数视为突发大额。 */
        public double spikeRatio = 3.0;
        /** 单笔绝对金额阈值。 */
        public double spikeAbsolute = 100000;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndicatorRef {
        public String refName;
        public String dimensionField = "merchantId";
        public int windowDays = 1;
        public String granularity = "DAY";
        public Double threshold;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KnownRisk {
        public String id;
        public String name;
        public String description;
        /** 命中条件：signals 中对应键为 true 时视为该已知风险命中。 */
        public List<String> signalKeys = new ArrayList<>();
        /** 建议调用的工具（供 LLM 编排参考）。 */
        public List<String> suggestTools = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuleConfig {
        public String when;
        public Double threshold;
        public String refName;
        public String decision;
        public String reason;
        public double confidence = 0.75;
    }

    public static AgentRuntimeConfig parse(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return new AgentRuntimeConfig();
        }
        try {
            return mapper.readValue(json, AgentRuntimeConfig.class);
        } catch (Exception e) {
            return new AgentRuntimeConfig();
        }
    }

    public boolean isLlmEnabled() {
        String mode = llmMode == null ? "HEURISTIC" : llmMode.toUpperCase(Locale.ROOT);
        return "LLM".equals(mode) || "HYBRID".equals(mode);
    }

    public boolean isHybrid() {
        return "HYBRID".equalsIgnoreCase(llmMode);
    }

    public boolean isOrchestrated() {
        return "ORCHESTRATED".equalsIgnoreCase(llmMode);
    }
}
