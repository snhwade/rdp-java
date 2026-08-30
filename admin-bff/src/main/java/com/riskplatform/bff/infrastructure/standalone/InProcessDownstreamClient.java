package com.riskplatform.bff.infrastructure.standalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.bff.domain.DownstreamClient;
import com.riskplatform.common.error.CommonErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * standalone BFF：在同一 JVM 内通过 MockMvc 调度嵌入式后端 {@code /api/v1/**}，不发起 HTTP。
 */
@Component
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class InProcessDownstreamClient implements DownstreamClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public InProcessDownstreamClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Object get(String baseUrl, String path, String authorization) {
        return exchange(HttpMethod.GET, path, null, authorization);
    }

    @Override
    public Object post(String baseUrl, String path, Object body, String authorization) {
        return exchange(HttpMethod.POST, path, body, authorization);
    }

    @Override
    public Object put(String baseUrl, String path, Object body, String authorization) {
        return exchange(HttpMethod.PUT, path, body, authorization);
    }

    @Override
    public Object delete(String baseUrl, String path, String authorization) {
        return exchange(HttpMethod.DELETE, path, null, authorization);
    }

    private Object exchange(HttpMethod method, String path, Object body, String authorization) {
        if (path != null && path.startsWith("/api/v1/ai/")) {
            return aiTrainingStub(method, path);
        }
        try {
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder;
            if (method == HttpMethod.GET) {
                builder = MockMvcRequestBuilders.get(path);
            } else if (method == HttpMethod.POST) {
                builder = MockMvcRequestBuilders.post(path);
            } else if (method == HttpMethod.PUT) {
                builder = MockMvcRequestBuilders.put(path);
            } else if (method == HttpMethod.DELETE) {
                builder = MockMvcRequestBuilders.delete(path);
            } else {
                throw new IllegalArgumentException("Unsupported method: " + method);
            }
            builder.accept(MediaType.APPLICATION_JSON);
            if (authorization != null && !authorization.isBlank()) {
                builder.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            if (body != null && (method == HttpMethod.POST || method == HttpMethod.PUT)) {
                builder.contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body));
            }
            MvcResult result = mockMvc.perform(builder).andReturn();
            int status = result.getResponse().getStatus();
            String responseBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (status >= 200 && status < 300) {
                if (responseBody == null || responseBody.isBlank()) {
                    return Map.of();
                }
                return objectMapper.readValue(responseBody, Object.class);
            }
            throw parseError(status, responseBody);
        } catch (DownstreamException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DownstreamException(503, CommonErrorCode.INTERNAL_ERROR.code(),
                    "进程内后端调度失败: " + ex.getMessage(), null);
        }
    }

    private Object aiTrainingStub(HttpMethod method, String path) {
        if (method == HttpMethod.GET && path != null && path.startsWith("/api/v1/ai/training-jobs")) {
            return Map.of("data", List.of(), "page", 1, "pageSize", 20, "total", 0);
        }
        if (path != null && path.startsWith("/api/v1/ai/training-schedules")) {
            if (method == HttpMethod.GET) {
                return Map.of("data", List.of());
            }
            if (method == HttpMethod.POST && path.endsWith("/run-now")) {
                return Map.of("outcome", "SKIPPED", "reason", "standalone 模式未启用 AI 训练服务");
            }
            if (method == HttpMethod.POST) {
                return Map.of(
                        "id", 1,
                        "name", "stub",
                        "enabled", false,
                        "cronExpression", "0 2 * * *",
                        "windowDays", 30
                );
            }
            if (method == HttpMethod.PUT) {
                return Map.of("id", 1, "enabled", false);
            }
            if (method == HttpMethod.DELETE) {
                return Map.of("deleted", true, "id", 1);
            }
        }
        if (path != null && path.startsWith("/api/v1/ai/models")) {
            if (method == HttpMethod.GET) {
                return Map.of("data", List.of());
            }
            if (method == HttpMethod.PUT) {
                return Map.of(
                        "modelKind", "fraud",
                        "currentVersion", null,
                        "scoringAvailable", false,
                        "scoringReason", "standalone 模式未启用 AI 训练服务",
                        "versions", List.of()
                );
            }
        }
        if (method == HttpMethod.POST && path != null && path.equals("/api/v1/ai/score")) {
            return Map.of("available", false, "reason", "standalone 模式未启用 AI 训练服务");
        }
        throw new DownstreamException(503, "SYSTEM.DEPENDENCY_UNAVAILABLE",
                "standalone 模式未启用 AI 训练服务", null);
    }

    @SuppressWarnings("unchecked")
    private DownstreamException parseError(int status, String bodyText) {
        String code = null;
        String message = bodyText;
        Map<String, String> fields = null;
        try {
            if (bodyText != null && !bodyText.isBlank()) {
                Map<String, Object> parsed = objectMapper.readValue(bodyText, Map.class);
                if (parsed.get("code") != null) {
                    code = String.valueOf(parsed.get("code"));
                }
                if (parsed.get("message") != null) {
                    message = String.valueOf(parsed.get("message"));
                }
                Object rawFields = parsed.get("fields");
                if (rawFields instanceof Map<?, ?> map) {
                    fields = (Map<String, String>) map;
                }
            }
        } catch (Exception ignore) {
            // 非标准错误体
        }
        return new DownstreamException(status, code, message, fields);
    }
}
