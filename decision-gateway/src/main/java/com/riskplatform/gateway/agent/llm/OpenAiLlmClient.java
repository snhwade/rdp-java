package com.riskplatform.gateway.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.gateway.domain.AiAdviseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions（一次性推理 + 自主编排多步）。
 */
public class OpenAiLlmClient implements LlmClientPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public OpenAiLlmClient(RestClient restClient, ObjectMapper objectMapper, String baseUrl) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AiAdviseResult advise(
            AgentRuntimeConfigView config,
            String eventTypeCode,
            String engineDecision,
            List<Map<String, Object>> trace,
            Map<String, Object> signals) {
        String apiKey = apiKey(config);
        if (apiKey == null) {
            return null;
        }
        try {
            String userPayload = objectMapper.writeValueAsString(Map.of(
                    "eventTypeCode", eventTypeCode,
                    "engineDecision", engineDecision,
                    "knownRisks", config.knownRisks() == null ? List.of() : config.knownRisks(),
                    "signals", signals,
                    "toolTrace", trace));
            Map<String, Object> body = chatBody(config, List.of(
                    Map.of("role", "system", "content", buildAdviseSystemPrompt(config)),
                    Map.of("role", "user", "content", userPayload)));

            Map<String, Object> resp = postChat(apiKey, body);
            String content = extractContent(resp);
            if (content == null) {
                return null;
            }
            Map<String, Object> parsed = objectMapper.readValue(content, Map.class);
            String decision = String.valueOf(parsed.getOrDefault("decision", "PASS")).toUpperCase(Locale.ROOT);
            double confidence = parseDouble(parsed.get("confidence"), 0.7);
            String reason = String.valueOf(parsed.getOrDefault("reason", "LLM 推理"));

            List<Map<String, Object>> fullTrace = new ArrayList<>(trace);
            fullTrace.add(Map.of("tool", "llm_reason", "output", parsed));

            return new AiAdviseResult(decision, confidence, reason, fullTrace);
        } catch (Exception ex) {
            log.warn("LLM Agent 推理失败: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public OrchestrationStep orchestrateStep(
            AgentRuntimeConfigView config,
            String eventTypeCode,
            String engineDecision,
            List<Map<String, Object>> toolCatalog,
            List<Map<String, Object>> trace,
            Map<String, Object> signals,
            int stepIndex) {
        String apiKey = apiKey(config);
        if (apiKey == null) {
            return null;
        }
        try {
            String userPayload = objectMapper.writeValueAsString(Map.of(
                    "step", stepIndex,
                    "eventTypeCode", eventTypeCode,
                    "engineDecision", engineDecision,
                    "knownRisks", config.knownRisks() == null ? List.of() : config.knownRisks(),
                    "availableTools", toolCatalog,
                    "signals", signals,
                    "toolTrace", trace));

            Map<String, Object> body = chatBody(config, List.of(
                    Map.of("role", "system", "content", buildOrchestrateSystemPrompt(config)),
                    Map.of("role", "user", "content", userPayload)));

            Map<String, Object> resp = postChat(apiKey, body);
            String content = extractContent(resp);
            if (content == null) {
                return null;
            }
            Map<String, Object> parsed = objectMapper.readValue(content, Map.class);
            String action = String.valueOf(parsed.getOrDefault("action", "finish")).toLowerCase(Locale.ROOT);

            if ("call_tool".equals(action)) {
                String tool = parsed.get("tool") == null ? null : String.valueOf(parsed.get("tool"));
                Map<String, Object> args = parsed.get("args") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m
                        : Map.of();
                return new OrchestrationStep(
                        "call_tool", tool, args, null, 0, null, List.of(), List.of());
            }

            String decision = String.valueOf(parsed.getOrDefault("decision", "PASS")).toUpperCase(Locale.ROOT);
            double confidence = parseDouble(parsed.get("confidence"), 0.7);
            String reason = String.valueOf(parsed.getOrDefault("reason", "LLM 编排完成"));
            return new OrchestrationStep(
                    "finish",
                    null,
                    Map.of(),
                    decision,
                    confidence,
                    reason,
                    parseMapList(parsed.get("knownHits")),
                    parseMapList(parsed.get("unknownFindings")));
        } catch (Exception ex) {
            log.warn("LLM 编排步骤失败 step={}: {}", stepIndex, ex.getMessage());
            return null;
        }
    }

    private String apiKey(AgentRuntimeConfigView config) {
        if (config.apiKey() != null && !config.apiKey().isBlank()) {
            return config.apiKey();
        }
        String key = System.getenv(config.apiKeyEnv() == null ? "DEEPSEEK_API_KEY" : config.apiKeyEnv());
        return key == null || key.isBlank() ? null : key;
    }

    private Map<String, Object> chatBody(AgentRuntimeConfigView config, List<Map<String, Object>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.model() == null ? "deepseek-v4-pro" : config.model());
        body.put("temperature", config.temperature());
        body.put("messages", messages);
        int maxTokens = config.maxTokens() > 0 ? config.maxTokens() : 1024;
        body.put("max_tokens", maxTokens);
        if (config.useJsonResponseFormat()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postChat(String apiKey, Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(baseUrl + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            log.warn("LLM HTTP {}: {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw ex;
        }
    }

    private static String buildAdviseSystemPrompt(AgentRuntimeConfigView config) {
        String base = config.systemPrompt() == null ? "" : config.systemPrompt();
        return base
                + "\n" + AgentLlmSupport.explorationInstructions()
                + "\n请以 JSON 返回: {\"decision\":\"PASS|REVIEW|REJECT\",\"confidence\":0.0-1.0,"
                + "\"reason\":\"...\",\"knownHits\":[{\"id\":\"...\",\"name\":\"...\"}],"
                + "\"unknownFindings\":[{\"hypothesis\":\"...\",\"evidence\":\"...\",\"severity\":1-5}]}";
    }

    private static String buildOrchestrateSystemPrompt(AgentRuntimeConfigView config) {
        String base = config.systemPrompt() == null ? "" : config.systemPrompt();
        return base
                + "\n" + AgentLlmSupport.explorationInstructions()
                + "\n你是风控 Agent 编排器。每一步仅做一件事："
                + "信息不足则 call_tool 获取证据；证据充分则 finish 并给出决策。"
                + "\n禁止按固定顺序机械调用全部工具；同一工具可重复调用，也可跳过无关工具。"
                + "\n调用工具 JSON: {\"action\":\"call_tool\",\"tool\":\"工具id\",\"args\":{...}}"
                + "\n结束 JSON: {\"action\":\"finish\",\"decision\":\"PASS|REVIEW|REJECT\",\"confidence\":0.0-1.0,"
                + "\"reason\":\"必须引用 toolTrace 中的具体证据\","
                + "\"knownHits\":[{\"id\":\"...\",\"name\":\"...\"}],"
                + "\"unknownFindings\":[{\"hypothesis\":\"...\",\"evidence\":\"...\",\"severity\":1-5}]}";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseMapList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String extractContent(Map<String, Object> resp) {
        if (resp == null) {
            return null;
        }
        Object choices = resp.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            return null;
        }
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> msg)) {
            return null;
        }
        Object content = msg.get("content");
        if (content != null) {
            String text = String.valueOf(content).trim();
            if (!text.isBlank()) {
                return unwrapJsonPayload(text);
            }
        }
        Object reasoning = msg.get("reasoning_content");
        if (reasoning != null) {
            String text = String.valueOf(reasoning).trim();
            if (!text.isBlank()) {
                return unwrapJsonPayload(text);
            }
        }
        return null;
    }

    /** 从纯 JSON 或 markdown 代码块中提取 JSON 字符串。 */
    private static String unwrapJsonPayload(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        int open = trimmed.indexOf('{');
        int close = trimmed.lastIndexOf('}');
        if (open >= 0 && close > open) {
            return trimmed.substring(open, close + 1);
        }
        return trimmed;
    }

    private static double parseDouble(Object raw, double defaultVal) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
