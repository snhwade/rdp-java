package com.riskplatform.ruleconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.strategy.StrategyCategory;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDef;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDefRepository;
import com.riskplatform.ruleconfig.domain.strategy.StrategyScope;
import com.riskplatform.ruleconfig.infrastructure.security.JwtService;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestDataMapper;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestRows;
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
 * 验证策略（Verify_Strategy）模块集成测试（risk-console-redesign 任务 5.5，R5.11 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实服务进程（{@code @SpringBootTest} RANDOM_PORT，
 * 真实 Spring 上下文 + Flyway 迁移，含 V21 策略扩展）与<strong>真实 MySQL</strong>，
 * 经真实 REST/AppService 全栈验证：
 * <ul>
 *   <li>创建验证策略（具体业务场景作用域），重新读回确认优先级与作用域列真实落库 —— R5.3/R5.4/R5.5</li>
 *   <li>创建验证策略（不限业务场景 ANY_SCENARIO 作用域），确认 any_scope 列真实落库 —— R5.4</li>
 *   <li>编辑验证策略（名称/优先级/作用域），重新读回确认更新落库 —— R5.3</li>
 *   <li>优先级范围校验（边界）：1 与 9999 被接受；0 与 10000 被拒绝并返回范围错误且不落库 —— R5.5/R5.6</li>
 *   <li>策略代码重复被拒绝（{@code BUSINESS.DUPLICATE}），且前缀相似的非重复 code 不被误判 —— R5.7</li>
 *   <li>列表查询仅返回验证策略（VERIFY），且包含本测试创建的策略 —— R5.1/R5.2</li>
 * </ul>
 * 每步均断言数据<strong>真实写入 MySQL 并可重新读回</strong>（经 {@link StrategyDefRepository}
 * 与 {@link IntegrationTestDataMapper} 直接回查 strategy_def 的 priority/scope_scenario_id/any_scope 列，R15.3）。
 *
 * <p>真实 MySQL 不可用时由 {@link AbstractMySqlIntegrationTest} 的前置校验<strong>失败而非跳过</strong>
 * （R15.2/R15.3）。
 *
 * <p>幂等/可重复：本测试仅创建以 {@value #MARKER} 前缀命名的临时草稿数据（验证策略 + 临时业务场景），
 * 并在每个用例前后按外键安全顺序清理，绝不污染既有种子数据。
 */
class VerifyStrategyIntegrationTest extends AbstractMySqlIntegrationTest {

    /** 临时数据命名前缀：保证与种子数据隔离，便于按前缀幂等清理。 */
    private static final String MARKER = "ZZIT_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StrategyDefRepository strategyDefRepository;

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

    /** 按命名前缀幂等清理临时数据，顺序遵循外键依赖（先策略后场景）。 */
    private void cleanupMarkerData() {
        testData.deleteStrategyDefByCodePattern(MARKER + "%");
        testData.deleteScenarios(MARKER + "%");
    }

    // —— R5.3 创建：即使请求具体场景，也固定落库为全场景通用 ——

    @Test
    void createWithConcreteScenarioScope_alwaysPersistsAsGlobal() {
        long scenarioId = createScenario("场景-验证策略");
        String code = MARKER + "VS_CONCRETE_" + runId;

        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "实名验证策略");
        body.put("priority", 100);
        body.put("anyScope", false);
        body.put("scopeScenarioId", scenarioId);
        body.put("paramsJson", "{\"verifyMode\":\"OTP\"}");

        ResponseEntity<String> created = postJson("/api/v1/verify-strategies", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode createdJson = parse(created);
        long id = createdJson.get("id").asLong();
        assertThat(id).isPositive();
        assertThat(createdJson.get("priority").asInt()).isEqualTo(100);
        assertThat(createdJson.get("anyScope").asBoolean()).isTrue();
        assertThat(createdJson.get("scopeScenarioId").isNull()).isTrue();

        IntegrationTestRows.StrategyDefRow row = testData.findStrategyDefById(id);
        assertThat(row.getCategory()).isEqualTo("VERIFY");
        assertThat(row.getPriority()).isEqualTo(100);
        assertThat(row.getScopeScenarioId()).isNull();
        assertThat(asBool(row.getAnyScope())).isTrue();

        StrategyDef persisted = strategyDefRepository.findById(id).orElseThrow();
        assertThat(persisted.getCategory()).isEqualTo(StrategyCategory.VERIFY);
        assertThat(persisted.getPriority()).isEqualTo(100);
        assertThat(persisted.getScope()).isNotNull();
        assertThat(persisted.getScope().isAnyScope()).isTrue();
        assertThat(persisted.getScope().getScenarioId()).isNull();

        JsonNode got = parse(getJson("/api/v1/verify-strategies/" + id));
        assertThat(got.get("code").asText()).isEqualTo(code);
        assertThat(got.get("priority").asInt()).isEqualTo(100);
        assertThat(got.get("anyScope").asBoolean()).isTrue();
    }

    // —— R5.4 创建（不限业务场景 ANY_SCENARIO 作用域）：真实落库 ——

    @Test
    void createWithAnyScenarioScope_persistsAnyScopeFlag() {
        String code = MARKER + "VS_ANY_" + runId;

        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "全局验证策略");
        body.put("priority", 1);
        body.put("anyScope", true);
        body.put("scopeScenarioId", null);

        ResponseEntity<String> created = postJson("/api/v1/verify-strategies", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long id = parse(created).get("id").asLong();
        assertThat(parse(created).get("anyScope").asBoolean()).isTrue();

        // 直接回查 MySQL：any_scope=1，scope_scenario_id 为空（R5.4）。
        IntegrationTestRows.StrategyDefRow row = testData.findStrategyDefById(id);
        assertThat(row.getPriority()).isEqualTo(1);
        assertThat(row.getScopeScenarioId()).isNull();
        assertThat(asBool(row.getAnyScope())).isTrue();

        // 领域仓储回读：不限业务场景。
        StrategyDef persisted = strategyDefRepository.findById(id).orElseThrow();
        assertThat(persisted.getScope()).isNotNull();
        assertThat(persisted.getScope().isAnyScope()).isTrue();
        assertThat(persisted.getScope().getScenarioId()).isNull();
    }

    // —— R5.3 编辑：更新名称/优先级/作用域并往返落库 ——

    @Test
    void updateVerifyStrategy_changesArePersisted() {
        long scenarioId = createScenario("场景-编辑");
        String code = MARKER + "VS_EDIT_" + runId;

        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "原始名称");
        body.put("priority", 500);
        body.put("anyScope", false);
        body.put("scopeScenarioId", scenarioId);
        long id = parse(postJson("/api/v1/verify-strategies", body)).get("id").asLong();

        // 编辑：改名 + 优先级 + 改为不限业务场景。
        Map<String, Object> update = new HashMap<>();
        update.put("name", "更新后的名称");
        update.put("priority", 9999);
        update.put("anyScope", true);
        update.put("scopeScenarioId", null);

        ResponseEntity<String> updated = putJson("/api/v1/verify-strategies/" + id, update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updatedJson = parse(updated);
        assertThat(updatedJson.get("name").asText()).isEqualTo("更新后的名称");
        assertThat(updatedJson.get("priority").asInt()).isEqualTo(9999);
        assertThat(updatedJson.get("anyScope").asBoolean()).isTrue();

        // 直接回查 MySQL：名称/优先级/any_scope 真实落库（R15.3）。
        IntegrationTestRows.StrategyDefRow row = testData.findStrategyDefById(id);
        assertThat(row.getName()).isEqualTo("更新后的名称");
        assertThat(row.getPriority()).isEqualTo(9999);
        assertThat(asBool(row.getAnyScope())).isTrue();

        // 领域仓储回读：作用域语义以 any_scope 为准，重建为「不限业务场景」（R5.4）。
        StrategyDef persisted = strategyDefRepository.findById(id).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("更新后的名称");
        assertThat(persisted.getPriority()).isEqualTo(9999);
        assertThat(persisted.getScope()).isNotNull();
        assertThat(persisted.getScope().isAnyScope()).isTrue();

        // REST 详情再读回一致（R5.3 往返）。
        JsonNode got = parse(getJson("/api/v1/verify-strategies/" + id));
        assertThat(got.get("name").asText()).isEqualTo("更新后的名称");
        assertThat(got.get("priority").asInt()).isEqualTo(9999);
        assertThat(got.get("anyScope").asBoolean()).isTrue();
    }

    // —— R5.5/R5.6 优先级范围校验（边界 1/9999 接受，0/10000 拒绝且不落库） ——

    @Test
    void priorityBoundaries_oneAndMaxAccepted() {
        long scenarioId = createScenario("场景-优先级边界");

        String lowCode = MARKER + "VS_PRI_LOW_" + runId;
        long lowId = parse(postJson("/api/v1/verify-strategies",
                strategyBody(lowCode, "下界策略", 1, false, scenarioId))).get("id").asLong();
        assertThat(strategyDefRepository.findById(lowId).orElseThrow().getPriority()).isEqualTo(1);

        String highCode = MARKER + "VS_PRI_HIGH_" + runId;
        long highId = parse(postJson("/api/v1/verify-strategies",
                strategyBody(highCode, "上界策略", 9999, false, scenarioId))).get("id").asLong();
        assertThat(strategyDefRepository.findById(highId).orElseThrow().getPriority()).isEqualTo(9999);
    }

    @Test
    void priorityBoundaries_zeroAndOverMaxRejected_andNotPersisted() {
        long scenarioId = createScenario("场景-优先级越界");

        // priority=0 越下界：拒绝（R5.6），范围错误，且不落库。
        String zeroCode = MARKER + "VS_PRI_ZERO_" + runId;
        ResponseEntity<String> tooLow = postJson("/api/v1/verify-strategies",
                strategyBody(zeroCode, "越下界策略", 0, false, scenarioId));
        assertThat(tooLow.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode lowErr = parse(tooLow);
        assertThat(lowErr.get("code").asText()).isEqualTo("VALIDATION.INVALID_FIELD");
        assertThat(lowErr.get("fields").has("priority")).as("应返回 priority 范围错误（R5.6）").isTrue();
        assertThat(countByCode(zeroCode)).as("越界策略不应落库（R5.6）").isZero();

        // priority=10000 越上界：拒绝（R5.6），范围错误，且不落库。
        String overCode = MARKER + "VS_PRI_OVER_" + runId;
        ResponseEntity<String> tooHigh = postJson("/api/v1/verify-strategies",
                strategyBody(overCode, "越上界策略", 10000, false, scenarioId));
        assertThat(tooHigh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode highErr = parse(tooHigh);
        assertThat(highErr.get("code").asText()).isEqualTo("VALIDATION.INVALID_FIELD");
        assertThat(highErr.get("fields").has("priority")).as("应返回 priority 范围错误（R5.6）").isTrue();
        assertThat(countByCode(overCode)).as("越界策略不应落库（R5.6）").isZero();
    }

    // —— R5.7 code 重复拒绝 + 前缀相似非重复不误判 ——

    @Test
    void duplicateCodeRejected_whilePrefixSimilarCodeNotMisjudged() {
        long scenarioId = createScenario("场景-重复校验");
        String code = MARKER + "VS_DUP_" + runId;

        ResponseEntity<String> first = postJson("/api/v1/verify-strategies",
                strategyBody(code, "首个策略", 100, false, scenarioId));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(countByCode(code)).isEqualTo(1);

        // 完全相同 code 第二次创建：拒绝（R5.7），且不重复落库。
        ResponseEntity<String> dup = postJson("/api/v1/verify-strategies",
                strategyBody(code, "重复策略", 200, true, null));
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(parse(dup).get("code").asText()).isEqualTo("BUSINESS.DUPLICATE");
        assertThat(countByCode(code)).as("重复 code 不应二次落库（R5.7）").isEqualTo(1);

        // 前缀相似但不相同的 code：精确等值去重，不应被误判为重复（R5.7）。
        String prefixSimilar = code + "_X";
        ResponseEntity<String> notDup = postJson("/api/v1/verify-strategies",
                strategyBody(prefixSimilar, "前缀相似策略", 300, false, scenarioId));
        assertThat(notDup.getStatusCode())
                .as("前缀相似的非重复 code 不应被误判为重复（R5.7）")
                .isEqualTo(HttpStatus.OK);
        assertThat(countByCode(prefixSimilar)).isEqualTo(1);
    }

    // —— R5.1/R5.2 列表仅返回验证策略且包含创建项 ——

    @Test
    void list_returnsOnlyVerifyStrategies_includingCreated() {
        long scenarioId = createScenario("场景-列表");
        String concreteCode = MARKER + "VS_LIST_A_" + runId;
        String anyCode = MARKER + "VS_LIST_B_" + runId;
        postJson("/api/v1/verify-strategies", strategyBody(concreteCode, "列表策略A", 10, false, scenarioId));
        postJson("/api/v1/verify-strategies", strategyBody(anyCode, "列表策略B", 20, true, null));

        // 插入一条非 VERIFY 策略（NOTIFY），直接经 IntegrationTestDataMapper 落库以验证列表过滤（R5.2）。
        String notifyCode = MARKER + "VS_NOTIFY_" + runId;
        testData.insertNotifyStrategy(notifyCode, "非验证策略");

        JsonNode list = parse(getJson("/api/v1/verify-strategies"));
        assertThat(list.isArray()).isTrue();
        List<String> codes = list.findValuesAsText("code");
        assertThat(codes).contains(concreteCode, anyCode);
        assertThat(codes).as("列表不应包含非 VERIFY 策略（R5.2）").doesNotContain(notifyCode);

        // 列表中每一项均为 VERIFY 类别（直接回查每个 code 的 category）。
        for (JsonNode item : list) {
            String itemCode = item.get("code").asText();
            String category = testData.findStrategyCategoryByCode(itemCode);
            assertThat(category).as("列表返回项 %s 应为 VERIFY", itemCode).isEqualTo("VERIFY");
        }
    }

    // —————————————————— 辅助方法 ——————————————————

    /** 构造一个验证策略创建/编辑请求体。 */
    private Map<String, Object> strategyBody(String code, String name, Integer priority,
                                             boolean anyScope, Long scopeScenarioId) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("priority", priority);
        body.put("anyScope", anyScope);
        body.put("scopeScenarioId", scopeScenarioId);
        return body;
    }

    /** 经 REST 创建一个带前缀的临时业务场景，返回其 id。 */
    private long createScenario(String name) {
        String code = MARKER + "SCN_" + runId + "_" + System.nanoTime();
        ResponseEntity<String> resp = postJson("/api/v1/scenarios", Map.of(
                "code", code, "name", name, "eventTypeCodes", List.of()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(resp).get("id").asLong();
    }

    private int countByCode(String code) {
        Integer n = testData.countStrategyDefByCode(code);
        return n == null ? 0 : n;
    }

    /** 归一化 MySQL TINYINT(1) 列值（0/1）为布尔。 */
    private boolean asBool(Integer value) {
        return value != null && value != 0;
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
