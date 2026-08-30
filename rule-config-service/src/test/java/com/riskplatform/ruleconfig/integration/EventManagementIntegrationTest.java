package com.riskplatform.ruleconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.eventtype.EventKind;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;
import com.riskplatform.ruleconfig.domain.eventtype.EventType;
import com.riskplatform.ruleconfig.domain.eventtype.EventTypeRepository;
import com.riskplatform.ruleconfig.infrastructure.security.JwtService;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestDataMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * 事件管理模块集成测试（risk-console-redesign 任务 2.6，R2.14 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实服务进程（{@code @SpringBootTest} RANDOM_PORT，
 * 真实 Spring 上下文 + Flyway 迁移）与<strong>真实 MySQL</strong>，经真实 REST/AppService 全栈验证：
 * <ul>
 *   <li>创建事件（含场景/用途多选/事件分型）—— R2.2/R2.3/R2.4</li>
 *   <li>编辑事件 —— R2.7</li>
 *   <li>无依赖时删除成功 —— R2.8</li>
 *   <li>事件代码重复拒绝 —— R2.6</li>
 *   <li>存在关联依赖（事件字段）时删除被拦截（{@code EVENT.HAS_DEPENDENCY}）且事件保留 —— R2.9</li>
 *   <li>列表查询与场景→事件树查询 —— R2.1</li>
 * </ul>
 * 每步均断言数据<strong>真实写入 MySQL 并可重新读回</strong>（经 {@link EventTypeRepository}
 * 与 {@link IntegrationTestDataMapper} 直接回查，R15.3）。
 *
 * <p>真实 MySQL 不可用时由 {@link AbstractMySqlIntegrationTest} 的前置校验<strong>失败而非跳过</strong>
 * （R15.2/R15.3）。
 *
 * <p>幂等/可重复：本测试仅创建以 {@value #MARKER} 前缀命名的临时草稿数据（事件/场景/字段/事件字段），
 * 并在每个用例前后按前缀清理，绝不污染既有种子数据。
 */
class EventManagementIntegrationTest extends AbstractMySqlIntegrationTest {

    /** 临时数据命名前缀：保证与种子数据隔离，便于按前缀幂等清理。 */
    private static final String MARKER = "ZZIT_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventTypeRepository eventTypeRepository;

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

    /** 按命名前缀幂等清理临时数据，顺序遵循外键依赖（先关联后主体）。 */
    private void cleanupMarkerData() {
        testData.deleteEventFieldsByEventCodePattern(MARKER + "%");
        testData.deleteEventTypes(MARKER + "%");
        testData.deleteFieldLibraryByCodePattern(MARKER + "%");
        testData.deleteScenarios(MARKER + "%");
    }

    // —— R2.2/R2.3/R2.4 创建 + R2.7 编辑 + R2.8 删除：全链路往返落库 ——

    @Test
    void create_edit_delete_roundTripsThroughRealMySql() {
        long scenarioId = createScenario("场景-事件管理");
        String code = MARKER + "EVT_CRUD_" + runId;

        // —— 创建（R2.2/R2.3/R2.4）——
        ResponseEntity<String> created = postJson("/api/v1/events", Map.of(
                "code", code,
                "name", "支付下单",
                "scenarioId", scenarioId,
                "purposes", List.of("COMPUTE", "DECISION"),
                "eventKind", "FACT"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode createdBody = parse(created);
        long eventId = createdBody.get("id").asLong();
        assertThat(eventId).isPositive();

        // 直接回查 MySQL：数据真实落库且可重新读回（R15.3）。
        EventType persisted = eventTypeRepository.findById(eventId).orElseThrow();
        assertThat(persisted.getCode()).isEqualTo(code);
        assertThat(persisted.getName()).isEqualTo("支付下单");
        assertThat(persisted.getScenarioId()).isEqualTo(scenarioId);
        assertThat(persisted.getEventKind()).isEqualTo(EventKind.FACT);
        assertThat(persisted.getPurposes())
                .containsExactlyInAnyOrder(EventPurpose.COMPUTE, EventPurpose.DECISION);
        assertThat(countEventRows(code)).isEqualTo(1);

        // —— 编辑（R2.7）：改名、改分型、改用途 ——
        ResponseEntity<String> edited = putJson("/api/v1/events/" + eventId, Map.of(
                "name", "支付下单-改",
                "scenarioId", scenarioId,
                "purposes", List.of("DECISION"),
                "eventKind", "DIMENSION"));
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);

        EventType afterEdit = eventTypeRepository.findById(eventId).orElseThrow();
        assertThat(afterEdit.getName()).isEqualTo("支付下单-改");
        assertThat(afterEdit.getEventKind()).isEqualTo(EventKind.DIMENSION);
        assertThat(afterEdit.getPurposes()).containsExactly(EventPurpose.DECISION);
        // code 不可变
        assertThat(afterEdit.getCode()).isEqualTo(code);

        // —— 删除（R2.8）：无关联依赖时删除成功 ——
        ResponseEntity<String> deleted = exchange("/api/v1/events/" + eventId, HttpMethod.DELETE, null);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eventTypeRepository.findById(eventId)).isEmpty();
        assertThat(countEventRows(code)).isZero();
    }

    // —— R2.6 事件代码重复拒绝 ——

    @Test
    void duplicateCode_isRejected_andNotPersistedTwice() {
        long scenarioId = createScenario("场景-重复校验");
        String code = MARKER + "EVT_DUP_" + runId;
        Map<String, Object> body = Map.of(
                "code", code,
                "name", "重复事件",
                "scenarioId", scenarioId,
                "purposes", List.of("COMPUTE"),
                "eventKind", "FACT");

        assertThat(postJson("/api/v1/events", body).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> dup = postJson("/api/v1/events", body);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(parse(dup).get("code").asText()).isEqualTo("BUSINESS.DUPLICATE");

        // 仅落库一条（R2.6：拒绝且不持久化重复事件）。
        assertThat(countEventRows(code)).isEqualTo(1);
    }

    // —— R2.9 关联依赖（事件字段）拦截删除，事件保留 ——

    @Test
    void deleteBlockedByDependency_retainsEvent() {
        long scenarioId = createScenario("场景-依赖拦截");
        String code = MARKER + "EVT_DEP_" + runId;

        long eventId = parse(postJson("/api/v1/events", Map.of(
                "code", code,
                "name", "带依赖事件",
                "scenarioId", scenarioId,
                "purposes", List.of("COMPUTE", "DECISION"),
                "eventKind", "DIMENSION"))).get("id").asLong();

        // 创建一个全局字段，并将其作为事件字段关联到该事件 —— 构成真实的关联依赖。
        long fieldId = createField(MARKER + "FLD_" + runId, "金额", "Double");
        ResponseEntity<String> association = postJson(
                "/api/v1/events/" + code + "/fields",
                Map.of("fieldId", fieldId, "purposes", List.of("COMPUTE"), "derived", false));
        assertThat(association.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(countEventFieldRows(code)).isEqualTo(1);

        // 删除应被拦截（R2.9）：EVENT.HAS_DEPENDENCY，事件保留。
        ResponseEntity<String> blocked = exchange("/api/v1/events/" + eventId, HttpMethod.DELETE, null);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(parse(blocked).get("code").asText()).isEqualTo("EVENT.HAS_DEPENDENCY");

        Optional<EventType> stillThere = eventTypeRepository.findById(eventId);
        assertThat(stillThere).isPresent();
        assertThat(countEventRows(code)).isEqualTo(1);
    }

    // —— R2.1 列表查询 + 场景→事件树查询 ——

    @Test
    void listAndScenarioTree_returnPersistedEvents() {
        long scenarioId = createScenario("场景-列表查询");
        String codeA = MARKER + "EVT_LISTA_" + runId;
        String codeB = MARKER + "EVT_LISTB_" + runId;

        parse(postJson("/api/v1/events", Map.of(
                "code", codeA, "name", "事件A", "scenarioId", scenarioId,
                "purposes", List.of("COMPUTE"), "eventKind", "FACT")));
        parse(postJson("/api/v1/events", Map.of(
                "code", codeB, "name", "事件B", "scenarioId", scenarioId,
                "purposes", List.of("DECISION"), "eventKind", "DIMENSION")));

        // 按场景列表（R2.1）
        JsonNode list = parse(getJson("/api/v1/events?scenarioId=" + scenarioId));
        assertThat(list.isArray()).isTrue();
        List<String> listedCodes = list.findValuesAsText("code");
        assertThat(listedCodes).contains(codeA, codeB);

        // 场景→事件树（R2.1）：找到本测试场景节点，其下事件包含 A、B
        JsonNode tree = parse(getJson("/api/v1/scenarios/tree"));
        JsonNode myScenario = null;
        for (JsonNode node : tree) {
            if (node.get("id").asLong() == scenarioId) {
                myScenario = node;
                break;
            }
        }
        assertThat(myScenario).as("场景树应包含本测试创建的场景节点").isNotNull();
        List<String> treeEventCodes = myScenario.get("events").findValuesAsText("code");
        assertThat(treeEventCodes).contains(codeA, codeB);
    }

    // —————————————————— 辅助方法 ——————————————————

    /** 经 REST 创建一个带前缀的临时业务场景，返回其 id。 */
    private long createScenario(String name) {
        String code = MARKER + "SCN_" + runId;
        ResponseEntity<String> resp = postJson("/api/v1/scenarios", Map.of(
                "code", code, "name", name, "eventTypeCodes", List.of()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(resp).get("id").asLong();
    }

    /** 经 REST 创建一个全局字段，返回其 id。 */
    private long createField(String code, String name, String dataType) {
        ResponseEntity<String> resp = postJson("/api/v1/fields", Map.of(
                "code", code, "name", name, "dataType", dataType, "label", name));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(resp).get("id").asLong();
    }

    private int countEventRows(String code) {
        Integer n = testData.countEventTypeByCode(code);
        return n == null ? 0 : n;
    }

    private int countEventFieldRows(String eventCode) {
        Integer n = testData.countEventFieldsByEventCode(eventCode);
        return n == null ? 0 : n;
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
