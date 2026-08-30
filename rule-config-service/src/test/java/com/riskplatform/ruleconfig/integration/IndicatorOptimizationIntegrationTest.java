package com.riskplatform.ruleconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.infrastructure.security.JwtService;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestDataMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 指标配置优化集成测试（IR1 / ID1 / IS1 / IV1）。
 */
class IndicatorOptimizationIntegrationTest extends AbstractMySqlIntegrationTest {

    private static final String MARKER = "ZZIT_IND_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private IntegrationTestDataMapper testData;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String runId;

    @BeforeEach
    void setUp() {
        token = jwtService.issue("it-operator", List.of("OPERATOR"));
        runId = Long.toString(System.nanoTime());
        cleanupMarkerData();
    }

    @AfterEach
    void tearDown() {
        cleanupMarkerData();
    }

    @Test
    void description_roundTripsThroughRealMySql() {
        String eventCode = createEvent("备注测试事件");
        String refName = MARKER + "DESC_" + runId;
        Map<String, Object> body = baseIndicatorBody(refName, eventCode);
        body.put("description", "集成测试备注-" + runId);

        ResponseEntity<String> created = postJson("/api/v1/indicator-definitions", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long id = parse(created).get("id").asLong();
        assertThat(parse(created).get("description").asText()).isEqualTo("集成测试备注-" + runId);
        assertThat(testData.findIndicatorDescription(id)).isEqualTo("集成测试备注-" + runId);
    }

    @Test
    void references_visible_and_deleteOfflineBlockedWhenReferenced() {
        String eventCode = createEvent("引用门禁事件");
        String refName = "ZZITind" + runId;
        long indicatorId = createIndicator(refName, eventCode, "引用测试指标");

        long ruleId = seedReferencingRule(refName, eventCode);
        assertThat(ruleId).isPositive();
        assertThat(testData.countRulesByCompiledExprPattern("%" + refName + "%")).isGreaterThan(0);

        JsonNode refs = parse(getJson("/api/v1/indicator-definitions/references?refName=" + refName));
        assertThat(refs.isArray()).isTrue();
        assertThat(refs.size()).isGreaterThan(0);
        assertThat(refs.get(0).asText()).contains("规则");

        ResponseEntity<String> offlineResp = putJson("/api/v1/indicator-definitions/" + indicatorId + "/offline", null);
        assertThat(offlineResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(offlineResp.getBody()).contains("引用");

        ResponseEntity<String> deleteResp = deleteJson("/api/v1/indicator-definitions/" + indicatorId);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(deleteResp.getBody()).contains("引用");

        testData.deleteRuleById(ruleId);
        assertThat(putJson("/api/v1/indicator-definitions/" + indicatorId + "/offline", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void runtimeStats_returnsSeededAccumulateAndReadMiss() {
        String eventCode = createEvent("运行统计事件");
        String refName = MARKER + "STATS_" + runId;
        createIndicator(refName, eventCode, "运行统计指标");

        testData.upsertRuntimeStats(refName, 7L);

        JsonNode stats = parse(getJson("/api/v1/indicator-definitions/runtime-stats?refName=" + refName));
        assertThat(stats.isArray()).isTrue();
        assertThat(stats.size()).isEqualTo(1);
        assertThat(stats.get(0).get("refName").asText()).isEqualTo(refName);
        assertThat(stats.get(0).get("readMissCount").asLong()).isEqualTo(7L);
        assertThat(stats.get(0).get("lastAccumulateAt").isNull()).isFalse();
    }

    @Test
    void definitionSnapshots_andRollback_restorePreviousVersion() {
        String eventCode = createEvent("快照回退事件");
        String refName = MARKER + "SNAP_" + runId;
        long id = createIndicator(refName, eventCode, "快照V0");

        putJson("/api/v1/indicator-definitions/" + id, updateBody(eventCode, "快照V1", "备注一"));
        putJson("/api/v1/indicator-definitions/" + id, updateBody(eventCode, "快照V2", "备注二"));
        putJson("/api/v1/indicator-definitions/" + id, updateBody(eventCode, "快照V3", "备注三"));

        JsonNode snapshots = parse(getJson("/api/v1/indicator-definitions/" + id + "/definition-snapshots"));
        assertThat(snapshots.isArray()).isTrue();
        assertThat(snapshots.size()).isGreaterThanOrEqualTo(2);

        ResponseEntity<String> rolled = postJson(
                "/api/v1/indicator-definitions/" + id + "/rollback-last-definition", null);
        assertThat(rolled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(rolled).get("name").asText()).isEqualTo("快照V1");
        assertThat(parse(rolled).get("description").asText()).isEqualTo("备注一");
        assertThat(testData.findIndicatorName(id)).isEqualTo("快照V1");
        assertThat(testData.findIndicatorDescription(id)).isEqualTo("备注一");
    }

    private long createIndicator(String refName, String eventCode, String name) {
        ResponseEntity<String> resp = postJson("/api/v1/indicator-definitions", baseIndicatorBody(refName, eventCode, name));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(resp).get("id").asLong();
    }

    private Map<String, Object> baseIndicatorBody(String refName, String eventCode) {
        return baseIndicatorBody(refName, eventCode, "指标-" + refName);
    }

    private Map<String, Object> baseIndicatorBody(String refName, String eventCode, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("refName", refName);
        body.put("name", name);
        body.put("eventTypeCodes", List.of(eventCode));
        body.put("dimensions", List.of("merchantId"));
        body.put("windowDays", 1);
        body.put("sliceGranularity", "DAY");
        body.put("accScript", "amount");
        return body;
    }

    private Map<String, Object> updateBody(String eventCode, String name, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", description);
        body.put("eventTypeCodes", List.of(eventCode));
        body.put("dimensions", List.of("merchantId"));
        body.put("windowDays", 1);
        body.put("sliceGranularity", "DAY");
        body.put("accScript", "amount");
        return body;
    }

    private long seedReferencingRule(String refName, String eventCode) {
        String expr = refName + " > 0";
        String code = MARKER + "RULE_" + runId;
        testData.insertReferencingRule(
                code, "引用规则-" + runId, eventCode, "{\"expr\":\"" + expr + "\"}", expr);
        Long id = testData.findRuleIdByCode(code);
        return id == null ? 0L : id;
    }

    private long createScenario(String name) {
        String code = MARKER + "SCN_" + runId + "_" + System.nanoTime();
        ResponseEntity<String> resp = postJson("/api/v1/scenarios", Map.of(
                "code", code, "name", name, "eventTypeCodes", List.of()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(resp).get("id").asLong();
    }

    private String createEvent(String name) {
        long scenarioId = createScenario(name + "-场景");
        String code = MARKER + "EVT_" + runId + "_" + System.nanoTime();
        ResponseEntity<String> resp = postJson("/api/v1/events", Map.of(
                "code", code,
                "name", name,
                "scenarioId", scenarioId,
                "purposes", List.of("DECISION"),
                "eventKind", "FACT"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return code;
    }

    private void cleanupMarkerData() {
        testData.deleteRulesByCodePattern(MARKER + "%");
        testData.deleteIndicatorSnapshots(MARKER + "%", "ZZITind%");
        testData.deleteRuntimeStats(MARKER + "%", "ZZITind%");
        testData.deleteIndicators(MARKER + "%", "ZZITind%");
        testData.deleteEventTypes(MARKER + "%");
        testData.deleteScenarios(MARKER + "%");
    }

    private ResponseEntity<String> postJson(String path, Object body) {
        return exchange(path, HttpMethod.POST, body);
    }

    private ResponseEntity<String> putJson(String path, Object body) {
        return exchange(path, HttpMethod.PUT, body);
    }

    private ResponseEntity<String> getJson(String path) {
        return exchange(path, HttpMethod.GET, null);
    }

    private ResponseEntity<String> deleteJson(String path) {
        return exchange(path, HttpMethod.DELETE, null);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        HttpEntity<String> entity;
        try {
            String json = body == null ? null : objectMapper.writeValueAsString(body);
            entity = new HttpEntity<>(json, headers);
        } catch (Exception e) {
            throw new IllegalStateException("序列化请求体失败", e);
        }
        return restTemplate.exchange(url(path), method, entity, String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private JsonNode parse(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("解析响应体失败: " + response.getBody(), e);
        }
    }
}
