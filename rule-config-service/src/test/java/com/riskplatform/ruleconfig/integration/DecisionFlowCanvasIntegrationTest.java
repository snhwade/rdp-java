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
 * 决策流画布编排集成测试（risk-console-redesign 任务 12.5，R9.9 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实服务进程（{@code @SpringBootTest} RANDOM_PORT，
 * 真实 Spring 上下文 + Flyway 迁移，含 V18 决策流版本表与 V25 版本状态列）与<strong>真实 MySQL</strong>，
 * 经真实 REST/AppService 全栈验证决策流画布编排的保存、结构校验与序列化往返：
 * <ul>
 *   <li><strong>画布内容保存（R9.8/R9.9）</strong>：创建一个含 START + RULE_PACKAGE + CONDITION_GATEWAY
 *       + 多个 END（带 endDecision）的画布，连线带分支标签（边条件 condition），节点带逐节点配置
 *       （END 的 endDecision、规则包节点的 refType/refId/config）。断言 nodes/edges/分支标签/节点配置
 *       真实序列化落库于 {@code decision_flow_version.snapshot_json} 与 {@code decision_flow.nodes_json/
 *       edges_json}，并在经 REST 重新读回与直接回查 MySQL 时<strong>忠实往返</strong>。</li>
 *   <li><strong>未到达结束节点校验（R9.5）</strong>：保存一个存在「可达但无后继且非 END」节点的画布，
 *       断言被拒绝（400 / VALIDATION.INVALID_FIELD），返回 {@code fields.edges} 字段级错误，且不落库。</li>
 *   <li><strong>结束节点决策结果配置（R9.6）</strong>：END 缺失 endDecision、END 非法 endDecision 均被拒绝
 *       （字段级 {@code fields.nodes} 错误，不落库）；合法 endDecision（REFUND/MANUAL_REVIEW/AUTO_PASS/
 *       AUTO_REJECT）保存成功并落库。</li>
 * </ul>
 * 每步均断言数据<strong>真实写入 MySQL 并可重新读回</strong>（经 {@link IntegrationTestDataMapper} 直接回查
 * decision_flow 与 decision_flow_version，R15.3）。
 *
 * <p><strong>执行链路产出（R9.7）覆盖说明</strong>：结束节点产出其配置决策结果属于引擎（rule-decision-engine）
 * 执行侧能力（任务 12.2 的 {@code EndDecisionResolver} / {@code DecisionFlowEvaluator}），由引擎模块的
 * 单元/集成测试覆盖（Property 28）。本 rule-config-service 集成测试聚焦保存 / 结构校验 / 序列化往返，
 * 并断言「引擎据以产出决策结果的 END 节点 endDecision 配置」确已真实落库于版本快照，使引擎执行链路有据可依。
 *
 * <p>真实 MySQL 不可用时由 {@link AbstractMySqlIntegrationTest} 的前置校验<strong>失败而非跳过</strong>
 * （R15.2/R15.3）。
 *
 * <p>幂等/可重复：本测试仅创建以 {@value #MARKER} 前缀命名的临时草稿数据（决策流 + 临时事件/场景），
 * 并在每个用例前后按外键安全顺序清理（先版本子表、后决策流主体、再事件/场景），绝不污染既有种子数据。
 */
class DecisionFlowCanvasIntegrationTest extends AbstractMySqlIntegrationTest {

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

    /** 按命名前缀幂等清理临时数据，顺序遵循外键依赖（先版本子表，后决策流主体，再事件/场景）。 */
    private void cleanupMarkerData() {
        testData.deleteDecisionFlowVersionsByFlowNamePattern(MARKER + "%");
        testData.deleteDecisionFlowsByNamePattern(MARKER + "%");
        testData.deleteEventTypes(MARKER + "%");
        testData.deleteScenarios(MARKER + "%");
    }

    // —— R9.8/R9.9 画布内容保存：nodes/edges/分支标签/节点配置真实序列化落库并忠实往返 ——

    @Test
    void saveCanvas_persistsNodesEdgesBranchLabelsAndNodeConfig_andRoundTrips() {
        String eventCode = createEvent("画布保存事件");
        String flowName = MARKER + "FLOW_SAVE_" + runId;

        // 画布：START → RULE_PACKAGE → CONDITION_GATEWAY →（通过分支）END(AUTO_PASS) /（默认分支）END(AUTO_REJECT)
        // 连线分支标签以边条件 condition 承载（如 "score >= 60"）；END 节点配置承载 endDecision。
        Map<String, Object> startNode = node("start", "START", null, null, null);
        Map<String, Object> rpNode = node("rp1", "RULE_PACKAGE", "RULE_PACKAGE", 1001L,
                "{\"name\":\"风险规则包\"}");
        Map<String, Object> gatewayNode = node("gw1", "CONDITION_GATEWAY", null, null,
                "{\"name\":\"评分网关\"}");
        Map<String, Object> endPass = node("endPass", "END", null, null,
                "{\"endDecision\":\"AUTO_PASS\"}");
        Map<String, Object> endReject = node("endReject", "END", null, null,
                "{\"endDecision\":\"AUTO_REJECT\"}");
        List<Map<String, Object>> nodes = List.of(startNode, rpNode, gatewayNode, endPass, endReject);

        // 边：分支标签（condition）= "score >= 60"（通过分支）；默认分支 isDefault=true（不通过兜底）。
        List<Map<String, Object>> edges = List.of(
                edge("start", "rp1", null, null, false),
                edge("rp1", "gw1", null, null, false),
                edge("gw1", "endPass", "score >= 60", null, false),
                edge("gw1", "endReject", null, null, true));

        Map<String, Object> body = new HashMap<>();
        body.put("name", flowName);
        body.put("eventTypeCode", eventCode);
        body.put("nodes", nodes);
        body.put("edges", edges);
        body.put("startNodeId", "start");

        ResponseEntity<String> created = postJson("/api/v1/decision-flows", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode createdJson = parse(created);
        long flowId = createdJson.get("id").asLong();
        assertThat(flowId).isPositive();

        // —— 直接回查 MySQL：decision_flow 主表 nodes_json/edges_json 真实落库（R15.3） ——
        IntegrationTestRows.DecisionFlowDetailRow flowRow = testData.findDecisionFlowDetailById(flowId);
        assertThat(flowRow.getName()).isEqualTo(flowName);
        assertThat(flowRow.getEventTypeCode()).isEqualTo(eventCode);
        assertThat(flowRow.getStartNodeId()).isEqualTo("start");
        JsonNode mainNodes = readTree(flowRow.getNodesJson());
        JsonNode mainEdges = readTree(flowRow.getEdgesJson());
        assertCanvasContent(mainNodes, mainEdges);

        // —— 决策流版本快照：nodes/edges/分支标签/节点配置序列化落库于 snapshot_json（R9.8） ——
        String snapshotJson = testData.findLatestDecisionFlowSnapshotJson(flowId);
        assertThat(snapshotJson).as("应写入版本快照（R9.8）").isNotBlank();
        JsonNode snapshot = readTree(snapshotJson);
        assertThat(snapshot.get("name").asText()).isEqualTo(flowName);
        assertThat(snapshot.get("eventTypeCode").asText()).isEqualTo(eventCode);
        assertThat(snapshot.get("startNodeId").asText()).isEqualTo("start");
        assertCanvasContent(snapshot.get("nodes"), snapshot.get("edges"));

        // —— REST 重新读回：画布内容忠实往返（R9.9） ——
        JsonNode got = parse(getJson("/api/v1/decision-flows/" + flowId));
        assertThat(got.get("name").asText()).isEqualTo(flowName);
        assertThat(got.get("startNodeId").asText()).isEqualTo("start");
        assertCanvasContent(got.get("nodes"), got.get("edges"));
    }

    /**
     * 断言画布内容（节点类型/逐节点配置含 endDecision、连线/分支标签 condition、默认分支 isDefault、
     * 规则包节点 refType/refId）与提交一致。对节点/边按标识定位后逐字段校验，保证忠实往返。
     */
    private void assertCanvasContent(JsonNode nodes, JsonNode edges) {
        assertThat(nodes).isNotNull();
        assertThat(nodes.isArray()).isTrue();
        assertThat(nodes.size()).isEqualTo(5);

        JsonNode start = findByField(nodes, "nodeId", "start");
        assertThat(start.get("type").asText()).isEqualTo("START");

        JsonNode rp = findByField(nodes, "nodeId", "rp1");
        assertThat(rp.get("type").asText()).isEqualTo("RULE_PACKAGE");
        assertThat(rp.get("refType").asText()).isEqualTo("RULE_PACKAGE");
        assertThat(rp.get("refId").asLong()).isEqualTo(1001L);
        // 规则包节点逐节点配置忠实保留
        assertThat(readTree(rp.get("config").asText()).get("name").asText()).isEqualTo("风险规则包");

        JsonNode gw = findByField(nodes, "nodeId", "gw1");
        assertThat(gw.get("type").asText()).isEqualTo("CONDITION_GATEWAY");

        // END 节点逐节点配置：endDecision 忠实保留（引擎据此产出决策结果，R9.6/R9.7）
        JsonNode endPass = findByField(nodes, "nodeId", "endPass");
        assertThat(endPass.get("type").asText()).isEqualTo("END");
        assertThat(readTree(endPass.get("config").asText()).get("endDecision").asText())
                .isEqualTo("AUTO_PASS");
        JsonNode endReject = findByField(nodes, "nodeId", "endReject");
        assertThat(readTree(endReject.get("config").asText()).get("endDecision").asText())
                .isEqualTo("AUTO_REJECT");

        assertThat(edges).isNotNull();
        assertThat(edges.isArray()).isTrue();
        assertThat(edges.size()).isEqualTo(4);

        // 分支标签（条件）忠实保留：网关→通过分支带条件 "score >= 60"
        JsonNode passEdge = findEdge(edges, "gw1", "endPass");
        assertThat(passEdge.get("condition").asText()).isEqualTo("score >= 60");
        assertThat(passEdge.get("isDefault").asBoolean()).isFalse();
        // 默认分支：isDefault=true（不通过兜底）
        JsonNode rejectEdge = findEdge(edges, "gw1", "endReject");
        assertThat(rejectEdge.get("isDefault").asBoolean()).isTrue();
        // 连接关系忠实保留
        assertThat(findEdge(edges, "start", "rp1")).isNotNull();
        assertThat(findEdge(edges, "rp1", "gw1")).isNotNull();
    }

    // —— R9.5 未到达结束节点校验：可达且无后继的非 END 节点 → 拒绝（字段级错误）且不落库 ——

    @Test
    void saveCanvas_withDanglingReachableNode_isRejected_andNotPersisted() {
        String eventCode = createEvent("未达结束事件");
        String flowName = MARKER + "FLOW_DANGLING_" + runId;

        // rp1 可达（start→rp1）但无任何出线且非 END → 存在不以 END 终止的可达路径（R9.5）。
        List<Map<String, Object>> nodes = List.of(
                node("start", "START", null, null, null),
                node("rp1", "RULE_PACKAGE", "RULE_PACKAGE", 1L, null),
                node("end", "END", null, null, "{\"endDecision\":\"AUTO_PASS\"}"));
        List<Map<String, Object>> edges = List.of(
                edge("start", "rp1", null, null, false),
                edge("start", "end", null, null, false));

        Map<String, Object> body = new HashMap<>();
        body.put("name", flowName);
        body.put("eventTypeCode", eventCode);
        body.put("nodes", nodes);
        body.put("edges", edges);
        body.put("startNodeId", "start");

        ResponseEntity<String> resp = postJson("/api/v1/decision-flows", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode err = parse(resp);
        assertThat(err.get("code").asText()).isEqualTo("VALIDATION.INVALID_FIELD");
        assertThat(err.get("fields").has("edges")).as("应返回 edges 字段级错误（R9.5）").isTrue();
        assertThat(err.get("fields").get("edges").asText()).contains("未到达结束(END)节点");

        // 不落库（R9.5）。
        assertThat(countFlowByName(flowName)).as("校验失败的决策流不应落库（R9.5）").isZero();
    }

    // —— R9.6 结束节点决策结果配置：缺失/非法拒绝且不落库；合法保存成功 ——

    @Test
    void saveCanvas_endNodeMissingDecision_isRejected_andNotPersisted() {
        String eventCode = createEvent("结束缺决策事件");
        String flowName = MARKER + "FLOW_END_MISSING_" + runId;

        // END 节点无任何配置（缺失 endDecision）→ 拒绝（R9.6）。
        List<Map<String, Object>> nodes = List.of(
                node("start", "START", null, null, null),
                node("end", "END", null, null, null));
        List<Map<String, Object>> edges = List.of(edge("start", "end", null, null, false));

        ResponseEntity<String> resp = postJson("/api/v1/decision-flows",
                flowBody(flowName, eventCode, nodes, edges));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode err = parse(resp);
        assertThat(err.get("code").asText()).isEqualTo("VALIDATION.INVALID_FIELD");
        assertThat(err.get("fields").has("nodes")).as("应返回 nodes 字段级错误（R9.6）").isTrue();
        assertThat(err.get("fields").get("nodes").asText()).contains("必须配置决策结果");

        assertThat(countFlowByName(flowName)).as("缺决策结果不应落库（R9.6）").isZero();
    }

    @Test
    void saveCanvas_endNodeIllegalDecision_isRejected_andNotPersisted() {
        String eventCode = createEvent("结束非法决策事件");
        String flowName = MARKER + "FLOW_END_ILLEGAL_" + runId;

        // END 节点 endDecision 取非法值 → 拒绝（R9.6）。
        List<Map<String, Object>> nodes = List.of(
                node("start", "START", null, null, null),
                node("end", "END", null, null, "{\"endDecision\":\"FOO\"}"));
        List<Map<String, Object>> edges = List.of(edge("start", "end", null, null, false));

        ResponseEntity<String> resp = postJson("/api/v1/decision-flows",
                flowBody(flowName, eventCode, nodes, edges));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode err = parse(resp);
        assertThat(err.get("code").asText()).isEqualTo("VALIDATION.INVALID_FIELD");
        assertThat(err.get("fields").get("nodes").asText()).contains("决策结果非法");

        assertThat(countFlowByName(flowName)).as("非法决策结果不应落库（R9.6）").isZero();
    }

    @Test
    void saveCanvas_allFourLegalEndDecisions_persistSuccessfully() {
        for (String decision : List.of("REFUND", "MANUAL_REVIEW", "AUTO_PASS", "AUTO_REJECT")) {
            String eventCode = createEvent("结束合法决策事件-" + decision);
            String flowName = MARKER + "FLOW_END_OK_" + decision + "_" + runId;

            List<Map<String, Object>> nodes = List.of(
                    node("start", "START", null, null, null),
                    node("end", "END", null, null, "{\"endDecision\":\"" + decision + "\"}"));
            List<Map<String, Object>> edges = List.of(edge("start", "end", null, null, false));

            ResponseEntity<String> resp = postJson("/api/v1/decision-flows",
                    flowBody(flowName, eventCode, nodes, edges));
            assertThat(resp.getStatusCode())
                    .as("合法 endDecision[%s] 应保存成功（R9.6）", decision)
                    .isEqualTo(HttpStatus.OK);
            long flowId = parse(resp).get("id").asLong();

            // END 决策结果真实落库于版本快照（引擎执行链路据此产出决策结果，R9.6/R9.7）。
            String snapshotJson = testData.findLatestDecisionFlowSnapshotJson(flowId);
            JsonNode snapshotNodes = readTree(snapshotJson).get("nodes");
            JsonNode endNode = findByField(snapshotNodes, "nodeId", "end");
            assertThat(readTree(endNode.get("config").asText()).get("endDecision").asText())
                    .isEqualTo(decision);
        }
    }

    // —— R8.4/R9.8 编辑保存：版本递增，新画布内容写入新版本快照并往返 ——

    @Test
    void updateCanvas_createsNewVersionSnapshot_withUpdatedContent() {
        String eventCode = createEvent("画布编辑事件");
        String flowName = MARKER + "FLOW_UPDATE_" + runId;

        // 初始最小画布 START→END(MANUAL_REVIEW)。
        List<Map<String, Object>> nodes = new ArrayList<>(List.of(
                node("start", "START", null, null, null),
                node("end", "END", null, null, "{\"endDecision\":\"MANUAL_REVIEW\"}")));
        List<Map<String, Object>> edges = new ArrayList<>(List.of(
                edge("start", "end", null, null, false)));
        long flowId = parse(postJson("/api/v1/decision-flows",
                flowBody(flowName, eventCode, nodes, edges))).get("id").asLong();

        int versionsAfterCreate = countVersions(flowId);
        assertThat(versionsAfterCreate).isEqualTo(1);

        // 编辑：新增规则包节点与新分支标签，END 决策结果改为 AUTO_REJECT。
        List<Map<String, Object>> newNodes = List.of(
                node("start", "START", null, null, null),
                node("rp1", "RULE_PACKAGE", "RULE_PACKAGE", 2002L, null),
                node("end", "END", null, null, "{\"endDecision\":\"AUTO_REJECT\"}"));
        List<Map<String, Object>> newEdges = List.of(
                edge("start", "rp1", "命中名单", null, false),
                edge("rp1", "end", null, null, false));

        Map<String, Object> update = new HashMap<>();
        update.put("name", flowName);
        update.put("nodes", newNodes);
        update.put("edges", newEdges);
        update.put("startNodeId", "start");
        update.put("status", "ENABLED");

        ResponseEntity<String> updated = putJson("/api/v1/decision-flows/" + flowId, update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 版本递增（保留历史，R8.4）。
        assertThat(countVersions(flowId)).isEqualTo(versionsAfterCreate + 1);

        // 最新版本快照含编辑后的内容（新规则包节点 + 新分支标签 + 新 endDecision，R9.8）。
        String latestSnapshot = testData.findLatestDecisionFlowSnapshotJson(flowId);
        JsonNode snapshot = readTree(latestSnapshot);
        JsonNode rp = findByField(snapshot.get("nodes"), "nodeId", "rp1");
        assertThat(rp.get("refId").asLong()).isEqualTo(2002L);
        JsonNode end = findByField(snapshot.get("nodes"), "nodeId", "end");
        assertThat(readTree(end.get("config").asText()).get("endDecision").asText())
                .isEqualTo("AUTO_REJECT");
        JsonNode listEdge = findEdge(snapshot.get("edges"), "start", "rp1");
        assertThat(listEdge.get("condition").asText()).isEqualTo("命中名单");

        // REST 读回主体反映最新画布（R9.9）。
        JsonNode got = parse(getJson("/api/v1/decision-flows/" + flowId));
        assertThat(got.get("nodes").size()).isEqualTo(3);
        assertThat(findByField(got.get("nodes"), "nodeId", "rp1").get("refId").asLong())
                .isEqualTo(2002L);
    }

    // —————————————————— 辅助方法 ——————————————————

    private Map<String, Object> node(String nodeId, String type, String refType, Long refId, String config) {
        Map<String, Object> n = new HashMap<>();
        n.put("nodeId", nodeId);
        n.put("type", type);
        n.put("refType", refType);
        n.put("refId", refId);
        n.put("config", config);
        return n;
    }

    private Map<String, Object> edge(String from, String to, String condition,
                                     Integer trafficPercent, boolean isDefault) {
        Map<String, Object> e = new HashMap<>();
        e.put("from", from);
        e.put("to", to);
        e.put("condition", condition);
        e.put("trafficPercent", trafficPercent);
        e.put("isDefault", isDefault);
        return e;
    }

    private Map<String, Object> flowBody(String name, String eventCode,
                                         List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("eventTypeCode", eventCode);
        body.put("nodes", nodes);
        body.put("edges", edges);
        body.put("startNodeId", "start");
        return body;
    }

    private JsonNode findByField(JsonNode array, String field, String value) {
        for (JsonNode item : array) {
            JsonNode f = item.get(field);
            if (f != null && value.equals(f.asText())) {
                return item;
            }
        }
        throw new AssertionError("未找到 " + field + "=" + value + " 的元素: " + array);
    }

    private JsonNode findEdge(JsonNode edges, String from, String to) {
        for (JsonNode e : edges) {
            if (from.equals(e.get("from").asText()) && to.equals(e.get("to").asText())) {
                return e;
            }
        }
        throw new AssertionError("未找到边 " + from + "->" + to + ": " + edges);
    }

    private int countFlowByName(String name) {
        Integer n = testData.countDecisionFlowByName(name);
        return n == null ? 0 : n;
    }

    private int countVersions(long flowId) {
        Integer n = testData.countDecisionFlowVersionsByFlowId(flowId);
        return n == null ? 0 : n;
    }

    /** 经 REST 创建一个带前缀的临时业务场景，返回其 id。 */
    private long createScenario(String name) {
        String code = MARKER + "SCN_" + runId + "_" + System.nanoTime();
        ResponseEntity<String> resp = postJson("/api/v1/scenarios", Map.of(
                "code", code, "name", name, "eventTypeCodes", List.of()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(resp).get("id").asLong();
    }

    /** 经 REST 创建一个挂在临时场景下的事件，返回其 code。 */
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
        return readTree(response.getBody());
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("解析 JSON 失败: " + json, e);
        }
    }
}
