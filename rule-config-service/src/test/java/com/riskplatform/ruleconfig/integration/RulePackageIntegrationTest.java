package com.riskplatform.ruleconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 规则包（Rule_Package）模块集成测试（risk-console-redesign 任务 8.4，R6.7/R6.8 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实服务进程（{@code @SpringBootTest} RANDOM_PORT，
 * 真实 Spring 上下文 + Flyway 迁移，含 V22 规则三态扩展）与<strong>真实 MySQL</strong>，
 * 经真实 REST/AppService 全栈验证：
 * <ul>
 *   <li><strong>创建规则包</strong>（HIT 与 SCORE 两种触发模式）经 POST，重新读回确认真实落库 —— R6.3</li>
 *   <li><strong>卡片墙三状态计数</strong>：在某规则包下播撒 ONLINE/TRIAL_RUN/OFFLINE 三态规则，
 *       GET {@code /api/v1/rule-packages?eventCode=} 返回的 {@code {online,trialRun,offline}} 计数
 *       恰等于该规则包按状态分组的真实行数 —— R6.6</li>
 *   <li><strong>规则列表查询</strong> GET {@code /{id}/rules} 返回规则编码/名称/状态/决策事件/风险等级/风险分值 —— R6.4</li>
 *   <li><strong>批量操作</strong> POST {@code /{id}/rules:batch}（上线/试运行/下线/删除）对全部选中规则生效，
 *       逐条返回结果，且状态变更/删除真实落库于 MySQL —— R6.5</li>
 * </ul>
 * 每步均断言数据<strong>真实写入 MySQL 并可重新读回</strong>（经 {@link IntegrationTestDataMapper} 直接回查
 * rule_package 与 rule_v2，R15.3）。
 *
 * <p>真实 MySQL 不可用时由 {@link AbstractMySqlIntegrationTest} 的前置校验<strong>失败而非跳过</strong>
 * （R15.2/R15.3）。
 *
 * <p>幂等/可重复：本测试仅创建以 {@value #MARKER} 前缀命名的临时草稿数据（规则包/规则/事件/场景），
 * 并在每个用例前后按外键安全顺序清理，绝不污染既有种子数据。
 */
class RulePackageIntegrationTest extends AbstractMySqlIntegrationTest {

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

    /** 按命名前缀幂等清理临时数据，顺序遵循外键依赖（先关联/子表，后主体）。 */
    private void cleanupMarkerData() {
        testData.deleteRulePackageRulesByPackageCodePattern(MARKER + "%");
        testData.deleteRulePackageEventsByPackageCodePattern(MARKER + "%");
        testData.deleteRulePackageScenariosByPackageCodePattern(MARKER + "%");
        testData.deleteRulePackageScoreBandsByPackageCodePattern(MARKER + "%");
        testData.deleteRulesByCodePattern(MARKER + "%");
        testData.deleteRulePackagesByCodePattern(MARKER + "%");
        testData.deleteEventTypes(MARKER + "%");
        testData.deleteScenarios(MARKER + "%");
    }

    // —— R6.3 创建规则包（HIT 与 SCORE 触发模式）：经 POST 全链路往返落库 ——

    @Test
    void createHitAndScorePackages_roundTripThroughRealMySql() {
        // HIT 触发模式
        String hitCode = MARKER + "PKG_HIT_" + runId;
        Map<String, Object> hitBody = new HashMap<>();
        hitBody.put("code", hitCode);
        hitBody.put("name", "命中规则包-" + runId);
        hitBody.put("triggerMode", "HIT");

        ResponseEntity<String> hitCreated = postJson("/api/v1/rule-packages", hitBody);
        assertThat(hitCreated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode hitJson = parse(hitCreated);
        long hitId = hitJson.get("id").asLong();
        assertThat(hitId).isPositive();
        assertThat(hitJson.get("triggerMode").asText()).isEqualTo("HIT");

        // 直接回查 MySQL：触发模式真实落库且不可变（R6.3）。
        IntegrationTestRows.RulePackageRow hitRow = testData.findRulePackageById(hitId);
        assertThat(hitRow.getCode()).isEqualTo(hitCode);
        assertThat(hitRow.getName()).isEqualTo("命中规则包-" + runId);
        assertThat(hitRow.getTriggerMode()).isEqualTo("HIT");

        // SCORE 触发模式
        String scoreCode = MARKER + "PKG_SCORE_" + runId;
        Map<String, Object> scoreBody = new HashMap<>();
        scoreBody.put("code", scoreCode);
        scoreBody.put("name", "评分规则包-" + runId);
        scoreBody.put("triggerMode", "SCORE");

        ResponseEntity<String> scoreCreated = postJson("/api/v1/rule-packages", scoreBody);
        assertThat(scoreCreated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode scoreJson = parse(scoreCreated);
        long scoreId = scoreJson.get("id").asLong();
        assertThat(scoreId).isPositive();
        assertThat(scoreJson.get("triggerMode").asText()).isEqualTo("SCORE");

        // 直接回查 MySQL：SCORE 包真实落库（R6.3）。
        IntegrationTestRows.RulePackageRow scoreRow = testData.findRulePackageById(scoreId);
        assertThat(scoreRow.getCode()).isEqualTo(scoreCode);
        assertThat(scoreRow.getTriggerMode()).isEqualTo("SCORE");

        // REST 详情再读回一致（R6.3 往返）。
        JsonNode got = parse(getJson("/api/v1/rule-packages/" + hitId));
        assertThat(got.get("code").asText()).isEqualTo(hitCode);
        assertThat(got.get("triggerMode").asText()).isEqualTo("HIT");
    }

    // —— R6.6 卡片墙三状态计数：与真实按状态分组行数严格相等 ——

    @Test
    void cardWallTristateCounts_matchRealPerStatusRowCounts() {
        String eventCode = createEvent("卡片墙计数事件");
        long pkgId = createPackageForEvent(MARKER + "PKG_COUNT_" + runId, "计数规则包", "HIT", eventCode);

        // 在该规则包下播撒三态规则：2 上线、3 试运行、1 下线。
        seedRule(MARKER + "R_ON_1_" + runId, pkgId, "ONLINE", eventCode);
        seedRule(MARKER + "R_ON_2_" + runId, pkgId, "ONLINE", eventCode);
        seedRule(MARKER + "R_TR_1_" + runId, pkgId, "TRIAL_RUN", eventCode);
        seedRule(MARKER + "R_TR_2_" + runId, pkgId, "TRIAL_RUN", eventCode);
        seedRule(MARKER + "R_TR_3_" + runId, pkgId, "TRIAL_RUN", eventCode);
        seedRule(MARKER + "R_OFF_1_" + runId, pkgId, "OFFLINE", eventCode);

        // 真实按状态分组行数（直接 SQL 聚合）作为基准。
        long realOnline = countByStatus(pkgId, "ONLINE");
        long realTrial = countByStatus(pkgId, "TRIAL_RUN");
        long realOffline = countByStatus(pkgId, "OFFLINE");
        assertThat(realOnline).isEqualTo(2);
        assertThat(realTrial).isEqualTo(3);
        assertThat(realOffline).isEqualTo(1);

        // 卡片墙 GET：定位本测试规则包卡片，断言三态计数与真实行数严格相等（R6.6）。
        JsonNode cards = parse(getJson("/api/v1/rule-packages?eventCode=" + eventCode));
        assertThat(cards.isArray()).isTrue();
        JsonNode myCard = findCardById(cards, pkgId);
        assertThat(myCard).as("卡片墙应包含本测试规则包卡片").isNotNull();
        JsonNode counts = myCard.get("counts");
        assertThat(counts.get("online").asLong()).isEqualTo(realOnline);
        assertThat(counts.get("trialRun").asLong()).isEqualTo(realTrial);
        assertThat(counts.get("offline").asLong()).isEqualTo(realOffline);
    }

    // —— R6.4 规则列表查询：返回规则编码/名称/状态/决策事件/风险等级/风险分值 ——

    @Test
    void listRules_returnsPersistedRulesWithExpectedFields() {
        String eventCode = createEvent("规则列表事件");
        long pkgId = createPackageForEvent(MARKER + "PKG_LIST_" + runId, "列表规则包", "HIT", eventCode);

        String ruleCode = MARKER + "R_LIST_" + runId;
        long ruleId = seedRule(ruleCode, pkgId, "ONLINE", eventCode);

        JsonNode rules = parse(getJson("/api/v1/rule-packages/" + pkgId + "/rules"));
        assertThat(rules.isArray()).isTrue();
        JsonNode myRule = null;
        for (JsonNode r : rules) {
            if (r.get("id").asLong() == ruleId) {
                myRule = r;
                break;
            }
        }
        assertThat(myRule).as("规则列表应包含播撒的规则").isNotNull();
        assertThat(myRule.get("code").asText()).isEqualTo(ruleCode);
        assertThat(myRule.get("name").asText()).isEqualTo("规则-" + ruleCode);
        assertThat(myRule.get("status").asText()).isEqualTo("ONLINE");
        assertThat(myRule.get("decisionEventCode").asText()).isEqualTo(eventCode);
        assertThat(myRule.get("riskLevelCode").asText()).isEqualTo("HIGH");
        assertThat(myRule.get("riskScore").asInt()).isEqualTo(80);
    }

    // —— R6.5 批量操作（上线/试运行/下线）：逐条结果完整且状态真实落库 ——

    @Test
    void batchStatusOperation_appliesToAllSelected_andPersists() {
        String eventCode = createEvent("批量状态事件");
        long pkgId = createPackageForEvent(MARKER + "PKG_BATCH_" + runId, "批量规则包", "HIT", eventCode);

        // 起始三条均为 OFFLINE。
        long r1 = seedRule(MARKER + "R_B1_" + runId, pkgId, "OFFLINE", eventCode);
        long r2 = seedRule(MARKER + "R_B2_" + runId, pkgId, "OFFLINE", eventCode);
        long r3 = seedRule(MARKER + "R_B3_" + runId, pkgId, "OFFLINE", eventCode);

        // 批量上线全部三条（R6.5）。
        Map<String, Object> batch = new HashMap<>();
        batch.put("operation", "ONLINE");
        batch.put("ruleIds", List.of(r1, r2, r3));
        ResponseEntity<String> resp = postJson("/api/v1/rule-packages/" + pkgId + "/rules:batch", batch);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode results = parse(resp);
        assertThat(results.isArray()).isTrue();
        // 逐条结果完整：选中 3 条 → 返回 3 条，且每条对应一个 ruleId 且 success。
        assertThat(results.size()).isEqualTo(3);
        for (JsonNode item : results) {
            assertThat(item.get("success").asBoolean()).isTrue();
            assertThat(item.get("ruleId").asLong()).isIn(r1, r2, r3);
        }

        // 状态变更真实落库（R15.3）：三条均为 ONLINE。
        assertThat(statusOf(r1)).isEqualTo("ONLINE");
        assertThat(statusOf(r2)).isEqualTo("ONLINE");
        assertThat(statusOf(r3)).isEqualTo("ONLINE");

        // 再批量试运行其中两条，确认状态切换落库。
        Map<String, Object> trialBatch = new HashMap<>();
        trialBatch.put("operation", "TRIAL_RUN");
        trialBatch.put("ruleIds", List.of(r1, r2));
        assertThat(postJson("/api/v1/rule-packages/" + pkgId + "/rules:batch", trialBatch)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(r1)).isEqualTo("TRIAL_RUN");
        assertThat(statusOf(r2)).isEqualTo("TRIAL_RUN");
        assertThat(statusOf(r3)).isEqualTo("ONLINE");
    }

    // —— R6.5 批量删除：对全部选中规则生效且真实从 MySQL 删除 ——

    @Test
    void batchDeleteOperation_removesAllSelected_fromRealMySql() {
        String eventCode = createEvent("批量删除事件");
        long pkgId = createPackageForEvent(MARKER + "PKG_DEL_" + runId, "删除规则包", "HIT", eventCode);

        long r1 = seedRule(MARKER + "R_D1_" + runId, pkgId, "ONLINE", eventCode);
        long r2 = seedRule(MARKER + "R_D2_" + runId, pkgId, "TRIAL_RUN", eventCode);

        Map<String, Object> batch = new HashMap<>();
        batch.put("operation", "DELETE");
        batch.put("ruleIds", List.of(r1, r2));
        ResponseEntity<String> resp = postJson("/api/v1/rule-packages/" + pkgId + "/rules:batch", batch);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode results = parse(resp);
        assertThat(results.size()).isEqualTo(2);
        for (JsonNode item : results) {
            assertThat(item.get("success").asBoolean()).isTrue();
        }

        // 真实从 MySQL 删除（R15.3）。
        assertThat(ruleExists(r1)).isFalse();
        assertThat(ruleExists(r2)).isFalse();
        assertThat(countByStatus(pkgId, "ONLINE")).isZero();
        assertThat(countByStatus(pkgId, "TRIAL_RUN")).isZero();
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

    /** 经 REST 创建一个 HIT/SCORE 规则包并关联到某决策事件（卡片墙按事件过滤需要），返回其 id。 */
    private long createPackageForEvent(String code, String name, String triggerMode, String eventCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name + "-" + runId);
        body.put("triggerMode", triggerMode);
        body.put("eventTypeCodes", List.of(eventCode));
        ResponseEntity<String> resp = postJson("/api/v1/rule-packages", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(resp).get("id").asLong();
    }

    /**
     * 直接经 IntegrationTestDataMapper 在 rule_v2 播撒一条规则（含三态 status 与决策事件/风险等级/风险分值），
     * 归属到指定规则包（rule_package_id 列与卡片墙计数、规则列表查询一致），返回新规则 id。
     */
    private long seedRule(String code, long rulePackageId, String status, String eventCode) {
        testData.insertSeedRule(code, "规则-" + code, rulePackageId, eventCode, status);
        Long id = testData.findRuleIdByCode(code);
        assertThat(id).as("播撒规则应有自增主键").isNotNull();
        return id;
    }

    private long countByStatus(long rulePackageId, String status) {
        Long n = testData.countRulesByPackageIdAndStatus(rulePackageId, status);
        return n == null ? 0L : n;
    }

    private String statusOf(long ruleId) {
        return testData.findRuleStatusById(ruleId);
    }

    private boolean ruleExists(long ruleId) {
        Integer n = testData.countRuleById(ruleId);
        return n != null && n > 0;
    }

    private JsonNode findCardById(JsonNode cards, long pkgId) {
        for (JsonNode card : cards) {
            if (card.get("id").asLong() == pkgId) {
                return card;
            }
        }
        return null;
    }

    // —— HTTP 辅助（携带真实 JWT） ——

    private ResponseEntity<String> postJson(String path, Object body) {
        return exchange(path, HttpMethod.POST, body);
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
