package com.riskplatform.gateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI Agent LLM 网关级默认配置（国内 DeepSeek OpenAI 兼容接口）。
 *
 * <p>策略 JSON 中的 {@code llm.*} 可覆盖此处默认值；未配置项自动回落到本配置。
 */
@ConfigurationProperties(prefix = "agent")
public class AgentLlmProperties {

    private Llm llm = new Llm();
    private Orchestration orchestration = new Orchestration();

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    public Orchestration getOrchestration() {
        return orchestration;
    }

    public void setOrchestration(Orchestration orchestration) {
        this.orchestration = orchestration;
    }

    public static class Llm {
        /** 提供方标识（展示用）。 */
        private String provider = "deepseek";
        /** OpenAI 兼容 API 根地址（不含 /v1）。 */
        private String baseUrl = "https://api.deepseek.com";
        private String defaultModel = "deepseek-v4-pro";
        /** 从此环境变量读取 API Key；也可在 application-local.yml 配置 api-key（勿提交仓库）。 */
        private String apiKeyEnv = "DEEPSEEK_API_KEY";
        /** 本地/测试用直连 Key，优先于环境变量（生产请只用 env）。 */
        private String apiKey;
        private double defaultTemperature = 0.2;
        private String defaultSystemPrompt =
                "你是资深支付风控 AI Agent。先核查 knownRisks；证据不足时可 PASS；"
                        + "对未知组合风险给出 unknownFindings。当前为影子模式，决策供对账参考。";
        /** 部分 OpenAI 兼容网关不支持 response_format，默认关闭，靠 system prompt 约束 JSON。 */
        private boolean useJsonResponseFormat = false;
        /** deepseek-v4-pro 等推理模型需要足够 token，避免 content 为空。 */
        private int maxTokens = 1024;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public double getDefaultTemperature() {
            return defaultTemperature;
        }

        public void setDefaultTemperature(double defaultTemperature) {
            this.defaultTemperature = defaultTemperature;
        }

        public String getDefaultSystemPrompt() {
            return defaultSystemPrompt;
        }

        public void setDefaultSystemPrompt(String defaultSystemPrompt) {
            this.defaultSystemPrompt = defaultSystemPrompt;
        }

        public boolean isUseJsonResponseFormat() {
            return useJsonResponseFormat;
        }

        public void setUseJsonResponseFormat(boolean useJsonResponseFormat) {
            this.useJsonResponseFormat = useJsonResponseFormat;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static class Orchestration {
        /** 策略未指定 llmMode 时的默认模式。 */
        private String defaultLlmMode = "ORCHESTRATED";
        private int defaultMaxSteps = 8;
        /** SHADOW / ADVISORY / STRICT / OVERRIDE，见 docs/enhancement-plan.md T1。 */
        private String defaultAdoptionMode = "SHADOW";
        /** 同步采纳模式等待 AI 的超时（毫秒）。 */
        private long aiSyncTimeoutMs = 8000;
        /**
         * LLM 不可用时是否允许启发式/默认决策回退。
         * 本地默认 true；生产 remote 建议 false，使失败进入 FAILED 而非静默 PASS。
         */
        private boolean allowHeuristicFallback = true;

        public String getDefaultLlmMode() {
            return defaultLlmMode;
        }

        public void setDefaultLlmMode(String defaultLlmMode) {
            this.defaultLlmMode = defaultLlmMode;
        }

        public int getDefaultMaxSteps() {
            return defaultMaxSteps;
        }

        public void setDefaultMaxSteps(int defaultMaxSteps) {
            this.defaultMaxSteps = defaultMaxSteps;
        }

        public String getDefaultAdoptionMode() {
            return defaultAdoptionMode;
        }

        public void setDefaultAdoptionMode(String defaultAdoptionMode) {
            this.defaultAdoptionMode = defaultAdoptionMode;
        }

        public long getAiSyncTimeoutMs() {
            return aiSyncTimeoutMs;
        }

        public void setAiSyncTimeoutMs(long aiSyncTimeoutMs) {
            this.aiSyncTimeoutMs = aiSyncTimeoutMs;
        }

        public boolean isAllowHeuristicFallback() {
            return allowHeuristicFallback;
        }

        public void setAllowHeuristicFallback(boolean allowHeuristicFallback) {
            this.allowHeuristicFallback = allowHeuristicFallback;
        }
    }
}
