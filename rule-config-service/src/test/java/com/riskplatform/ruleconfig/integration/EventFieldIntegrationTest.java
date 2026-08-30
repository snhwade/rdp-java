package com.riskplatform.ruleconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.eventfield.EventField;
import com.riskplatform.ruleconfig.domain.eventfield.EventFieldRepository;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;
import com.riskplatform.ruleconfig.infrastructure.security.JwtService;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestDataMapper;
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
 * 事件字段（事件下的字段）模块集成测试（risk-console-redesign 任务 4.5，R4.10 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实服务进程（{@code @SpringBootTest} RANDOM_PORT，
 * 真实 Spring 上下文 + Flyway 迁移）与<strong>真实 MySQL</strong>，经真实 REST/AppService 全栈验证：
 * <ul>
 *   <li>从字段库添加全局字段到事件，事件—字段关联真实落库（{@code event_field} 行）—— R4.2</li>
 *   <li>同事件下重复关联同一字段被拒绝（{@code BUSINESS.DUPLICATE}），不创建重复行 —— R4.4</li>
 *   <li>标记衍生字段，重新读回确认衍生标记持久化 —— R4.5</li>
 *   <li>未被引用的事件字段移除成功（关联行被删除）—— R4.6</li>
 *   <li>仍被引用的事件字段移除被拒绝（{@code EVENT_FIELD.IN_USE}），关联保留 —— R4.7</li>
 * </ul>
 * 每步均断言事件—字段关联<strong>真实写入 MySQL 并可重新读回</strong>（经 {@link EventFieldRepository}
 * 与 {@link IntegrationTestDataMapper} 直接回查，R15.3）。
 *
 * <p>R4.7 引用拦截的「被引用」条件按引用检查器的真实判定来源构造：检查器以
 * 「事件 code + 字段 code」在 {@code rule_v2.condition_json LIKE %fieldCode%} 中按包含匹配。
 * 本测试经 {@link IntegrationTestDataMapper} 直接向 {@code rule_v2} 插入一条引用该字段 code 的规则行以模拟在用状态，
 * 断言移除被拦截后清理该引用行，再断言移除成功（R4.6/R4.7）。
 *
 * <p>真实 MySQL 不可用时由 {@link AbstractMySqlIntegrationTest} 的前置校验<strong>失败而非跳过</strong>
 * （R15.2/R15.3）。
 *
 * <p>幂等/可重复：本测试仅创建以 {@value #MARKER} 前缀命名的临时草稿数据（场景/事件/字段/事件字段/规则），
 * 并在每个用例前后按外键安全顺序清理，绝不污染既有种子数据。
 */
class EventFieldIntegrationTest extends AbstractMySqlIntegrationTest {

    /** 临时数据命名前缀：保证与种子数据隔离，便于按前缀幂等清理。 */
    private static final String MARKER = "ZZIT_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventFieldRepository eventFieldRepository;

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

    /** 按命名前缀幂等清理临时数据，顺序遵循外键依赖（先关联/引用后主体）。 */
    private void cleanupMarkerData() {
        testData.deleteRulesByCodePattern(MARKER + "%");
        testData.deleteEventFieldsByEventCodePattern(MARKER + "%");
        testData.deleteEventTypes(MARKER + "%");
        testData.deleteFieldLibraryByCodePattern(MARKER + "%");
        testData.deleteScenarios(MARKER + "%");
    }

    // —— R4.2 添加全局字段到事件 + R4.5 衍生标记：全链路往返落库 ——

    @Test
    void addGlobalFieldToEvent_andMarkDerived_roundTripsThroughRealMySql() {
        long scenarioId = createScenario("场景-事件字段");
        String eventCode = createEvent(scenarioId, MARKER + "EVT_EF_" + runId);
        long fieldId = createField(MARKER + "FLD_EF_" + runId, MARKER + "NAME_EF_" + runId, "Double");

        // —— 从字段库添加字段到事件（R4.2）——
        ResponseEntity<String> added = postJson("/api/v1/events/" + eventCode + "/fields", Map.of(
                "fieldId", fieldId,
                "purposes", List.of("COMPUTE", "DECISION"),
                "derived", false));
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
        long eventFieldId = parse(added).get("id").asLong();
        assertThat(eventFieldId).isPositive();

        // 直接回查 MySQL：事件—字段关联真实落库且可重新读回（R15.3）。
        assertThat(countEventFieldRows(eventCode, fieldId)).isEqualTo(1);
        EventField persisted = eventFieldRepository.findById(eventFieldId).orElseThrow();
        assertThat(persisted.getEventTypeCode()).isEqualTo(eventCode);
        assertThat(persisted.getFieldId()).isEqualTo(fieldId);
        assertThat(persisted.isDerived()).isFalse();
        assertThat(persisted.getPurposes())
                .containsExactlyInAnyOrder(EventPurpose.COMPUTE, EventPurpose.DECISION);

        // —— 标记衍生字段（R4.5）：再重新读回确认衍生标记持久化 ——
        ResponseEntity<String> marked = putJson(
                "/api/v1/events/" + eventCode + "/fields/" + eventFieldId + "/derived",
                Map.of("derived", true));
        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(marked).get("derived").asBoolean()).isTrue();

        EventField afterMark = eventFieldRepository.findById(eventFieldId).orElseThrow();
        assertThat(afterMark.isDerived()).as("衍生标记应真实落库可读回（R4.5）").isTrue();
        // 数据库列亦应为 1。
        Integer derivedFlag = testData.findEventFieldDerivedFlag(eventFieldId);
        assertThat(derivedFlag).isEqualTo(1);

        // 列表查询应返回该事件字段（R4.1，联接字段库 code/名称/类型）。
        JsonNode list = parse(getJson("/api/v1/events/" + eventCode + "/fields"));
        assertThat(list.isArray()).isTrue();
        assertThat(list.findValuesAsText("fieldCode")).contains(MARKER + "FLD_EF_" + runId);
    }

    // —— R4.4 同事件下重复关联同一字段被拒绝 ——

    @Test
    void duplicateAssociation_isRejected_andNotPersistedTwice() {
        long scenarioId = createScenario("场景-重复关联");
        String eventCode = createEvent(scenarioId, MARKER + "EVT_DUP_" + runId);
        long fieldId = createField(MARKER + "FLD_DUP_" + runId, "国家", "String");

        Map<String, Object> body = Map.of(
                "fieldId", fieldId,
                "purposes", List.of("COMPUTE"),
                "derived", false);

        assertThat(postJson("/api/v1/events/" + eventCode + "/fields", body).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // 第二次将同一字段添加到同一事件应被拒绝（R4.4）。
        ResponseEntity<String> dup = postJson("/api/v1/events/" + eventCode + "/fields", body);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(parse(dup).get("code").asText()).isEqualTo("BUSINESS.DUPLICATE");

        // 仅落库一条关联（R4.4：拒绝且不创建重复关联）。
        assertThat(countEventFieldRows(eventCode, fieldId)).isEqualTo(1);
    }

    // —— R4.6 未被引用移除成功 + R4.7 被引用移除拦截 ——

    @Test
    void removeUnreferenced_succeeds_whileReferenced_isBlockedWithInUse() {
        long scenarioId = createScenario("场景-引用拦截");
        String eventCode = createEvent(scenarioId, MARKER + "EVT_REF_" + runId);
        String fieldCode = MARKER + "FLD_REF_" + runId;
        long fieldId = createField(fieldCode, "金额", "Double");

        long eventFieldId = parse(postJson("/api/v1/events/" + eventCode + "/fields", Map.of(
                "fieldId", fieldId,
                "purposes", List.of("DECISION"),
                "derived", false))).get("id").asLong();
        assertThat(countEventFieldRows(eventCode, fieldId)).isEqualTo(1);

        // —— R4.7：插入一条引用该字段 code 的规则行以模拟「在用」状态 ——
        // 引用检查器以 rule_v2.event_type_code = ? AND condition_json LIKE %fieldCode% 判定。
        String ruleCode = MARKER + "RULE_REF_" + runId;
        testData.insertEventFieldReferencingRule(ruleCode, MARKER + "引用规则", eventCode,
                "{\"op\":\"GT\",\"field\":\"" + fieldCode + "\",\"value\":100}");

        // 被引用时移除应被拒绝（R4.7）：EVENT_FIELD.IN_USE，关联保留。
        ResponseEntity<String> blocked = exchange(
                "/api/v1/events/" + eventCode + "/fields/" + eventFieldId, HttpMethod.DELETE, null);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(parse(blocked).get("code").asText()).isEqualTo("EVENT_FIELD.IN_USE");
        assertThat(eventFieldRepository.findById(eventFieldId)).as("被引用关联应保留（R4.7）").isPresent();
        assertThat(countEventFieldRows(eventCode, fieldId)).isEqualTo(1);

        // —— 清理引用，使该事件字段不再被引用 ——
        testData.deleteRuleByCode(ruleCode);

        // —— R4.6：未被引用时移除成功，关联行被删除 ——
        ResponseEntity<String> removed = exchange(
                "/api/v1/events/" + eventCode + "/fields/" + eventFieldId, HttpMethod.DELETE, null);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eventFieldRepository.findById(eventFieldId)).as("无引用时移除成功（R4.6）").isEmpty();
        assertThat(countEventFieldRows(eventCode, fieldId)).isZero();
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

    /** 经 REST 创建一个事件，返回其 code。 */
    private String createEvent(long scenarioId, String code) {
        ResponseEntity<String> resp = postJson("/api/v1/events", Map.of(
                "code", code,
                "name", "事件" + code,
                "scenarioId", scenarioId,
                "purposes", List.of("COMPUTE", "DECISION"),
                "eventKind", "FACT"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return code;
    }

    /** 经 REST 创建一个全局字段，返回其 id。 */
    private long createField(String code, String name, String dataType) {
        ResponseEntity<String> resp = postJson("/api/v1/fields", Map.of(
                "code", code, "name", name, "dataType", dataType, "label", name));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(resp).get("id").asLong();
    }

    private int countEventFieldRows(String eventCode, long fieldId) {
        Integer n = testData.countEventFieldByEventAndField(eventCode, fieldId);
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
