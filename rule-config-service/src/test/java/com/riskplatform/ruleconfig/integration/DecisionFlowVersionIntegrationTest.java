package com.riskplatform.ruleconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.infrastructure.security.JwtService;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestDataMapper;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestRows;
import java.util.ArrayList;
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
 * 决策流（Decision_Flow）版本管理模块集成测试（risk-console-redesign 任务 11.4，
 * R8.9/R8.10 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实服务进程（{@code @SpringBootTest} RANDOM_PORT，
 * 真实 Spring 上下文 + Flyway 迁移，含 V18 决策流版本表与 V25 版本上下线状态列）与
 * <strong>真实 MySQL</strong>，经真实 REST/AppService 全栈验证：
 * <ul>
 *   <li><strong>创建决策流</strong>（POST，仅 name + eventTypeCode 的最小画布），重新读回确认
 *       决策流真实落库，且自动播种版本 1 快照（START→END，END 预置 MANUAL_REVIEW）—— R8.2</li>
 *   <li><strong>版本新建</strong>：PUT 保存创建<strong>新版本并保留历史</strong>；断言版本计数递增、
 *       历史快照仍可读回（snapshot_json 非空）—— R8.4</li>
 *   <li><strong>版本历史查询</strong>：GET {@code /{id}/versions} 返回全部版本，含版本号与上下线状态 —— R8.5</li>
 *   <li><strong>上下线版本切换</strong>：POST {@code /{id}/versions/{v}:online} 将该版本置 ONLINE 且原
 *       上线版本置 OFFLINE（断言任一时刻至多一个 ONLINE）；POST {@code /{id}:offline} 将上线版本置
 *       OFFLINE（断言 ONLINE 计数归零）—— R8.6/R8.7</li>
 * </ul>
 * 每步均断言数据<strong>真实写入 MySQL 并可重新读回</strong>（经 {@link IntegrationTestDataMapper} 直接回查
 * decision_flow 与 decision_flow_version，R15.3）。
 *
 * <p>真实 MySQL 不可用时由 {@link AbstractMySqlIntegrationTest} 的前置校验<strong>失败而非跳过</strong>
 * （R15.2/R15.3）。
 *
 * <p>幂等/可重复：本测试仅创建以 {@value #MARKER} 前缀命名的临时草稿数据（决策流 + 其版本快照），
 * 并在每个用例前后按外键安全顺序清理（先版本后主体），绝不污染既有种子数据。
 */
class DecisionFlowVersionIntegrationTest extends AbstractMySqlIntegrationTest {

    /** 临时数据命名前缀：保证与种子数据隔离，便于按前缀幂等清理。 */
    private static final String MARKER = "ZZIT_";

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
        // 真实 JWT：写操作需 OPERATOR/ADMIN，GET 亦需认证（SecurityConfig）。
        token = jwtService.issue("it-operator", List.of("OPERATOR"));
        runId = Long.toString(System.nanoTime());
        cleanupMarkerData();
    }

    @AfterEach
    void tearDown() {
        cleanupMarkerData();
    }

    /** 按命名前缀幂等清理临时数据，顺序遵循外键依赖（先版本快照，后决策流主体，再事件/场景）。 */
    private void cleanupMarkerData() {
        testData.deleteDecisionFlowVersionsByFlowNamePattern(MARKER + "%");
        testData.deleteDecisionFlowsByNamePattern(MARKER + "%");
        testData.deleteEventTypes(MARKER + "%");
        testData.deleteScenarios(MARKER + "%");
    }

    // —— R8.2 创建决策流（最小画布）：真实落库 + 自动播种版本 1 ——

    @Test
    void createFlow_persistsAndSeedsVersionOne() {
        String name = MARKER + "FLOW_CREATE_" + runId;
        String eventCode = createEvent("创建决策流事件");

        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("eventTypeCode", eventCode);

        ResponseEntity<String> created = postJson("/api/v1/decision-flows", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode createdJson = parse(created);
        long flowId = createdJson.get("id").asLong();
        assertThat(flowId).isPositive();
        assertThat(createdJson.get("name").asText()).isEqualTo(name);
        assertThat(createdJson.get("eventTypeCode").asText()).isEqualTo(eventCode);

        // 直接回查 MySQL：决策流主体真实落库（R15.3）。
        IntegrationTestRows.DecisionFlowRow row = testData.findDecisionFlowById(flowId);
        assertThat(row.getName()).isEqualTo(name);
        assertThat(row.getEventTypeCode()).isEqualTo(eventCode);

        // 自动播种版本 1 快照（R8.2）：decision_flow_version 恰有 1 行，版本号 1，默认下线。
        assertThat(versionCount(flowId)).isEqualTo(1);
        IntegrationTestRows.DecisionFlowVersionRow ver = testData.findDecisionFlowVersionByFlowId(flowId);
        assertThat(ver.getVersion()).isEqualTo(1);
        assertThat(ver.getStatus()).isEqualTo("OFFLINE");
        assertThat(ver.getSnapshotJson()).isNotBlank();

        // REST 版本历史再读回一致（R8.5 往返）。
        JsonNode versions = parse(getJson("/api/v1/decision-flows/" + flowId + "/versions"));
        assertThat(versions.isArray()).isTrue();
        assertThat(versions.size()).isEqualTo(1);
        assertThat(versions.get(0).get("version").asInt()).isEqualTo(1);
        assertThat(versions.get(0).get("status").asText()).isEqualTo("OFFLINE");
    }

    // —— R8.4 版本新建：PUT 保存创建新版本并保留历史 ——

    @Test
    void saveCreatesNewVersion_preservingHistory() {
        long flowId = createMinimalFlow(MARKER + "FLOW_SAVE_" + runId);
        assertThat(versionCount(flowId)).as("创建后应有版本 1").isEqualTo(1);

        // 第一次保存编辑 → 版本 2（AUTO_PASS 结束）。
        ResponseEntity<String> save1 = putJson("/api/v1/decision-flows/" + flowId,
                updateBody("更新名-2", "AUTO_PASS"));
        assertThat(save1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(versionCount(flowId)).as("一次保存后版本数应递增到 2（R8.4）").isEqualTo(2);

        // 第二次保存编辑 → 版本 3（AUTO_REJECT 结束）。
        ResponseEntity<String> save2 = putJson("/api/v1/decision-flows/" + flowId,
                updateBody("更新名-3", "AUTO_REJECT"));
        assertThat(save2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(versionCount(flowId)).as("两次保存后版本数应递增到 3（R8.4）").isEqualTo(3);

        // 版本号单调递增 1→2→3，且历史快照全部仍可读回（snapshot_json 非空，R8.4）。
        List<IntegrationTestRows.VersionSnapshotRow> rows = testData.findDecisionFlowVersionSnapshots(flowId);
        assertThat(rows).hasSize(3);
        for (int i = 0; i < rows.size(); i++) {
            assertThat(rows.get(i).getVersion()).isEqualTo(i + 1);
            assertThat(rows.get(i).getSnapshotJson())
                    .as("历史版本 %d 快照应保留且可读回（R8.4）", i + 1)
                    .isNotBlank();
        }

        // REST 历史亦返回全部 3 个版本（降序）。
        JsonNode versions = parse(getJson("/api/v1/decision-flows/" + flowId + "/versions"));
        assertThat(versions.size()).isEqualTo(3);
        List<Integer> versionNumbers = new ArrayList<>();
        versions.forEach(v -> versionNumbers.add(v.get("version").asInt()));
        assertThat(versionNumbers).containsExactlyInAnyOrder(1, 2, 3);
    }

    // —— R8.5 版本历史查询：返回全部版本及版本号与状态 ——

    @Test
    void versionHistory_returnsAllVersionsWithNumbersAndStatuses() {
        long flowId = createMinimalFlow(MARKER + "FLOW_HIST_" + runId);
        putJson("/api/v1/decision-flows/" + flowId, updateBody("历史-2", "AUTO_PASS"));
        putJson("/api/v1/decision-flows/" + flowId, updateBody("历史-3", "MANUAL_REVIEW"));

        JsonNode versions = parse(getJson("/api/v1/decision-flows/" + flowId + "/versions"));
        assertThat(versions.isArray()).isTrue();
        assertThat(versions.size()).isEqualTo(3);
        for (JsonNode v : versions) {
            // 每个版本均带版本号与上下线状态（R8.5）。
            assertThat(v.has("version")).isTrue();
            assertThat(v.get("version").asInt()).isBetween(1, 3);
            assertThat(v.has("status")).isTrue();
            assertThat(v.get("status").asText()).isIn("ONLINE", "OFFLINE");
        }
    }

    // —— R8.6/R8.7 上下线版本切换：至多一个上线版本，下线后归零 ——

    @Test
    void onlineOfflineSwitching_keepsAtMostOneOnline_andOfflineClearsIt() {
        long flowId = createMinimalFlow(MARKER + "FLOW_TOGGLE_" + runId);
        putJson("/api/v1/decision-flows/" + flowId, updateBody("切换-2", "AUTO_PASS"));
        putJson("/api/v1/decision-flows/" + flowId, updateBody("切换-3", "AUTO_REJECT"));
        assertThat(versionCount(flowId)).isEqualTo(3);

        // 上线版本 1（R8.6）。
        ResponseEntity<String> online1 = postJson(
                "/api/v1/decision-flows/" + flowId + "/versions/1:online", null);
        assertThat(online1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(flowId, 1)).isEqualTo("ONLINE");
        assertThat(onlineCount(flowId)).as("至多一个上线版本（R8.6）").isEqualTo(1);

        // 上线版本 2：版本 2 置 ONLINE，原上线版本 1 自动置 OFFLINE（R8.6）。
        ResponseEntity<String> online2 = postJson(
                "/api/v1/decision-flows/" + flowId + "/versions/2:online", null);
        assertThat(online2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(flowId, 2)).isEqualTo("ONLINE");
        assertThat(statusOf(flowId, 1)).as("原上线版本应被自动下线（R8.6）").isEqualTo("OFFLINE");
        assertThat(onlineCount(flowId)).as("任一时刻上线版本数不超过 1（R8.6）").isEqualTo(1);

        // REST 历史确认仅版本 2 处于 ONLINE。
        JsonNode versions = parse(getJson("/api/v1/decision-flows/" + flowId + "/versions"));
        for (JsonNode v : versions) {
            if (v.get("version").asInt() == 2) {
                assertThat(v.get("status").asText()).isEqualTo("ONLINE");
            } else {
                assertThat(v.get("status").asText()).isEqualTo("OFFLINE");
            }
        }

        // 决策流下线（R8.7）：当前上线版本（v2）置 OFFLINE，上线版本数归零。
        ResponseEntity<String> offline = postJson(
                "/api/v1/decision-flows/" + flowId + ":offline", null);
        assertThat(offline.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(flowId, 2)).isEqualTo("OFFLINE");
        assertThat(onlineCount(flowId)).as("下线后上线版本数为 0（R8.7）").isZero();
    }

    // —————————————————— 辅助方法 ——————————————————

    /** 经 REST 创建一个带前缀的临时业务场景，返回其 id。 */
    private long createScenario(String name) {
        String code = MARKER + "SCN_" + runId + "_" + System.nanoTime();
        ResponseEntity<String> resp = postJson("/api/v1/scenarios", Map.of(
                "code", code, "name", name, "eventTypeCodes", List.of()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(resp).get("id").asLong();
    }

    /** 经 REST 创建一个挂在临时场景下的事件，返回其 code（上线前校验要求 event_type 真实存在）。 */
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

    /** 经 REST 以「名称 + 事件」最小画布创建决策流（自动播种版本 1），返回其 id。 */
    private long createMinimalFlow(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("eventTypeCode", createEvent(name + "-事件"));
        ResponseEntity<String> resp = postJson("/api/v1/decision-flows", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(resp).get("id").asLong();
    }

    /**
     * 构造一个合法的 PUT 保存请求体：START→END 画布，END 节点携带指定的合法决策结果，
     * 以通过保存期结构校验（R9.5/R9.6，任务 12.1 已启用 END 决策结果校验）。
     */
    private Map<String, Object> updateBody(String name, String endDecision) {
        Map<String, Object> startNode = new HashMap<>();
        startNode.put("nodeId", "start");
        startNode.put("type", "START");
        startNode.put("config", null);

        Map<String, Object> endNode = new HashMap<>();
        endNode.put("nodeId", "end");
        endNode.put("type", "END");
        endNode.put("config", "{\"endDecision\":\"" + endDecision + "\"}");

        Map<String, Object> edge = new HashMap<>();
        edge.put("from", "start");
        edge.put("to", "end");
        edge.put("isDefault", false);

        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("nodes", List.of(startNode, endNode));
        body.put("edges", List.of(edge));
        body.put("startNodeId", "start");
        body.put("status", "ENABLED");
        return body;
    }

    private int versionCount(long flowId) {
        Integer n = testData.countDecisionFlowVersionsByFlowId(flowId);
        return n == null ? 0 : n;
    }

    private int onlineCount(long flowId) {
        Integer n = testData.countOnlineDecisionFlowVersionsByFlowId(flowId);
        return n == null ? 0 : n;
    }

    private String statusOf(long flowId, int version) {
        return testData.findDecisionFlowVersionStatus(flowId, version);
    }

    // —— HTTP 辅助（携带真实 JWT） ——

    private ResponseEntity<String> postJson(String path, Object body) {
        return exchange(path, HttpMethod.POST, body);
    }

    private ResponseEntity<String> putJson(String path, Object body) {
        return exchange(path, HttpMethod.PUT, body);
    }

    private ResponseEntity<String> getJson(String path) {
        return exchange(path, HttpMethod.GET, null);
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
