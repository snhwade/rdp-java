package com.riskplatform.bff.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.bff.domain.DownstreamClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.Map;

/**
 * 基于 {@link WebClient} 的下游调用实现（R14.1/R14.2/R17.1）。
 *
 * <p>同步阻塞调用下游服务（BFF 为 MVC 模型）。透传 Authorization 头实现 JWT 透传；
 * 下游返回非 2xx 时，解析其结构化错误体 {@code { code, message, fields }} 并以
 * {@link DownstreamException} 向上抛出，保留字段级错误供前端表单回显。
 */
public class WebClientDownstreamClient implements DownstreamClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WebClientDownstreamClient(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Object get(String baseUrl, String path, String authorization) {
        return exchange(HttpMethod.GET, baseUrl, path, null, authorization);
    }

    @Override
    public Object post(String baseUrl, String path, Object body, String authorization) {
        return exchange(HttpMethod.POST, baseUrl, path, body, authorization);
    }

    @Override
    public Object put(String baseUrl, String path, Object body, String authorization) {
        return exchange(HttpMethod.PUT, baseUrl, path, body, authorization);
    }

    @Override
    public Object delete(String baseUrl, String path, String authorization) {
        return exchange(HttpMethod.DELETE, baseUrl, path, null, authorization);
    }

    private Object exchange(HttpMethod method, String baseUrl, String path, Object body, String authorization) {
        try {
            WebClient.RequestBodySpec spec = webClient.method(method)
                    .uri(baseUrl + path)
                    .accept(MediaType.APPLICATION_JSON);
            if (authorization != null && !authorization.isBlank()) {
                spec.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            WebClient.RequestHeadersSpec<?> headersSpec = spec;
            if (body != null) {
                headersSpec = spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
            }
            return headersSpec.retrieve().bodyToMono(Object.class).block();
        } catch (WebClientResponseException ex) {
            throw toDownstreamException(ex);
        } catch (WebClientRequestException ex) {
            throw new DownstreamException(503, "SYSTEM.DEPENDENCY_UNAVAILABLE",
                    "下游服务不可用: " + describeTarget(baseUrl, path) + " — " + ex.getMessage(), null);
        }
    }

    /** 从 baseUrl 推断服务名与端口，避免一律误报 8082。 */
    static String describeTarget(String baseUrl, String path) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        String hint;
        if (url.contains(":8000")) {
            hint = "ai-training-service(8000)";
        } else if (url.contains(":8081")) {
            hint = "decision-gateway(8081)";
        } else if (url.contains(":8082")) {
            hint = "rule-config-service(8082)";
        } else if (url.contains(":8083")) {
            hint = "rule-decision-engine(8083)";
        } else if (url.contains(":8084")) {
            hint = "indicator-store-service(8084)";
        } else if (url.contains(":8085")) {
            hint = "screening-service(8085)";
        } else if (url.contains(":8086")) {
            hint = "merchant-rating-service(8086)";
        } else {
            hint = url.isBlank() ? "unknown" : url;
        }
        String p = path == null ? "" : path;
        return hint + (p.isBlank() ? "" : " path=" + p);
    }

    /** 将下游非 2xx 响应解析为携带结构化错误体的领域异常（R14.2 字段级透传）。 */
    @SuppressWarnings("unchecked")
    private DownstreamException toDownstreamException(WebClientResponseException ex) {
        String code = null;
        String message = ex.getMessage();
        Map<String, String> fields = null;
        try {
            String bodyText = ex.getResponseBodyAsString();
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
            // 下游错误体非标准结构时，仅透传状态码与原始消息
        }
        return new DownstreamException(ex.getStatusCode().value(), code, message, fields);
    }
}
