package com.riskplatform.bff.adapter;

import com.riskplatform.bff.application.BffAggregationService;
import com.riskplatform.bff.domain.DownstreamClient;
import com.riskplatform.bff.domain.DownstreamClient.DownstreamException;
import com.riskplatform.bff.infrastructure.config.DownstreamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BFF 聚合接口契约测试（R14.1/R14.2/R17.1）。
 *
 * <p>使用 MockMvc standaloneSetup + 替身 {@link DownstreamClient}（不连真实下游）验证：
 * <ul>
 *   <li>聚合转发到正确的下游基址与路径；</li>
 *   <li>JWT 透传：Authorization 头原样转发给下游；</li>
 *   <li>错误透传：下游字段级错误体 { code, message, fields } 原样返回，状态码透传。</li>
 * </ul>
 */
class BffControllerTest {

    /** 记录调用参数的下游替身，可预置正常返回或抛出下游异常。 */
    static class RecordingClient implements DownstreamClient {
        String lastBaseUrl;
        String lastPath;
        String lastAuth;
        Object lastBody;
        Object response = Map.of("ok", true);
        RuntimeException toThrow;

        private Object record(String baseUrl, String path, Object body, String auth) {
            this.lastBaseUrl = baseUrl;
            this.lastPath = path;
            this.lastBody = body;
            this.lastAuth = auth;
            if (toThrow != null) {
                throw toThrow;
            }
            return response;
        }

        @Override
        public Object get(String baseUrl, String path, String authorization) {
            return record(baseUrl, path, null, authorization);
        }

        @Override
        public Object post(String baseUrl, String path, Object body, String authorization) {
            return record(baseUrl, path, body, authorization);
        }

        @Override
        public Object put(String baseUrl, String path, Object body, String authorization) {
            return record(baseUrl, path, body, authorization);
        }

        @Override
        public Object delete(String baseUrl, String path, String authorization) {
            return record(baseUrl, path, null, authorization);
        }
    }

    private RecordingClient client;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        client = new RecordingClient();
        DownstreamProperties props = new DownstreamProperties();
        props.setRuleConfig("http://rule-config:8082");
        props.setDecisionGateway("http://gateway:8081");
        props.setRuleDecisionEngine("http://engine:8083");
        props.setIndicatorStore("http://indicator:8084");
        props.setScreening("http://screening:8085");
        props.setMerchantRating("http://rating:8086");
        props.setAiTraining("http://ai:8000");
        BffAggregationService aggregation = new BffAggregationService(client, props);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RuleConfigBffController(aggregation), new OpsBffController(aggregation))
                .setControllerAdvice(new BffExceptionHandler())
                .build();
    }

    @Test
    void listEventTypes_forwardsToRuleConfig_withJwtPassThrough() throws Exception {
        mockMvc.perform(get("/bff/api/v1/event-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        assertThat(client.lastBaseUrl).isEqualTo("http://rule-config:8082");
        assertThat(client.lastPath).isEqualTo("/api/v1/event-types");
        // JWT 透传
        assertThat(client.lastAuth).isEqualTo("Bearer token-123");
    }

    @Test
    void queryOrders_forwardsQueryToGateway() throws Exception {
        mockMvc.perform(get("/bff/api/v1/orders").param("merchantId", "M001"))
                .andExpect(status().isOk());

        assertThat(client.lastBaseUrl).isEqualTo("http://gateway:8081");
        assertThat(client.lastPath).startsWith("/api/v1/orders?");
        assertThat(client.lastPath).contains("merchantId=M001");
    }

    @Test
    void getTrace_forwardsToEngine() throws Exception {
        mockMvc.perform(get("/bff/api/v1/trace/evt-1"))
                .andExpect(status().isOk());
        assertThat(client.lastBaseUrl).isEqualTo("http://engine:8083");
        assertThat(client.lastPath).isEqualTo("/api/v1/trace/evt-1");
    }

    @Test
    void downstreamFieldError_isMappedThrough_withStatusAndFields() throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("code", "编码不能为空");
        client.toThrow = new DownstreamException(400, "VALIDATION.INVALID_FIELD", "字段校验失败", fields);

        mockMvc.perform(post("/bff/api/v1/event-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION.INVALID_FIELD"))
                .andExpect(jsonPath("$.fields.code").value("编码不能为空"));
    }

    @Test
    void downstreamServerError_isMappedThrough() throws Exception {
        client.toThrow = new DownstreamException(503, "SYSTEM.DEPENDENCY_UNAVAILABLE", "依赖服务不可用", null);

        mockMvc.perform(get("/bff/api/v1/merchants/M1/rating"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SYSTEM.DEPENDENCY_UNAVAILABLE"));
    }

    // 引用 List 以避免未使用告警（保持与既有测试风格一致的占位）
    @SuppressWarnings("unused")
    private static final List<String> SUPPORTED_PAGES = List.of("event-types", "orders", "trace");
}
