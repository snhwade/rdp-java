package com.riskplatform.ruleconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.infrastructure.security.JwtService;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestDataMapper;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestRows;
import java.math.BigDecimal;
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
 * 评级模型与等级区间模块集成测试（risk-console-redesign 任务 14.6，
 * R10.9 / R11.6 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实服务进程（{@code @SpringBootTest} RANDOM_PORT，
 * 真实 Spring 上下文 + Flyway 迁移，含 V23 评级模型相关表）与<strong>真实 MySQL</strong>，
 * 经真实 REST/AppService 全栈验证：
 * <ul>
 *   <li><strong>评级模型创建</strong>（POST name+eventTypeCode+executionMode+subject+gradingMode），
 *       重新读回确认评级模型真实落库，且自动写入版本 1 快照 —— R10.2</li>
 *   <li><strong>执行方式与主体校验</strong>：非法 executionMode（非 REALTIME/SCHEDULED）或
 *       subject（非 MERCHANT/INDIVIDUAL）被拒绝（字段级错误）且不落库 —— R10.3</li>
 *   <li><strong>版本新建</strong>：PUT 保存创建<strong>新版本并保留历史</strong>；断言版本计数递增、
 *       历史快照仍可读回（snapshot_json 非空）—— R10.6</li>
 *   <li><strong>版本历史查询</strong>：详情接口返回全部版本，含版本号与状态元数据 —— R10.5</li>
 *   <li><strong>上下线</strong>：{@code :online} / {@code :offline} 更新状态并真实落库 —— R10.7</li>
 *   <li><strong>等级区间任意数量保存</strong>：5+ 连续区间保存成功并真实落库 —— R11.2</li>
 *   <li><strong>区间重叠拦截</strong>：存在重叠的区间被拒绝（字段级错误）且不落库 —— R11.4</li>
 *   <li><strong>覆盖缺口拦截</strong>：存在覆盖缺口的区间被拒绝（字段级错误）且不落库 —— R11.4</li>
 *   <li><strong>读取</strong>：已保存的等级区间可从 MySQL 重新读回 —— R15.3</li>
 * </ul>
 * 每步均断言数据<strong>真实写入 MySQL 并可重新读回</strong>（经 {@link IntegrationTestDataMapper} 直接回查
 * rating_model / rating_grade_band / rating_model_version，R15.3）。
 *
 * <p>真实 MySQL 不可用时由 {@link AbstractMySqlIntegrationTest} 的前置校验<strong>失败而非跳过</strong>
 * （R15.2/R15.3）。
 *
 * <p>幂等/可重复：本测试仅创建以 {@value #MARKER} 前缀命名的临时草稿数据（评级模型 + 其版本/区间/子项，
 * 及所属临时事件/场景），并在每个用例前后按外键安全顺序清理，绝不污染既有种子数据。
 */
class RatingModelIntegrationTest extends AbstractMySqlIntegrationTest {

    /** 临时数据命名前缀：保证与种子数据隔离，便于按前缀幂等清理。 */
    private static final String MARKER = "ZZIT_RM_";

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

    /** 按命名前缀幂等清理临时数据，顺序遵循外键依赖（先版本/区间/子项从表，后评级模型主体，再事件/场景）。 */
    private void cleanupMarkerData() {
        testData.deleteRatingGradeBandsByModelNamePattern(MARKER + "%");
        testData.deleteRatingItemsByModelNamePattern(MARKER + "%");
        testData.deleteRatingModelVersionsByModelNamePattern(MARKER + "%");
        testData.deleteRatingModelsByNamePattern(MARKER + "%");
        testData.deleteEventTypes(MARKER + "%");
        testData.deleteScenarios(MARKER + "%");
    }

    // —— R10.2 创建评级模型：真实落库 + 自动写入版本 1 快照 ——

    @Test
    void createRatingModel_persistsAndSeedsVersionOne() {
        String eventCode = createEvent("场景-评级创建", "评级事件");
        String name = MARKER + "MODEL_CREATE_" + runId;

        ResponseEntity<String> created = postJson("/api/v1/rating-models",
                createBody(name, eventCode, "REALTIME", "MERCHANT", "SCORE_BASED"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = parse(created);
        long modelId = body.get("id").asLong();
        assertThat(modelId).isPositive();
        assertThat(body.get("name").asText()).isEqualTo(name);
        assertThat(body.get("eventTypeCode").asText()).isEqualTo(eventCode);
        assertThat(body.get("executionMode").asText()).isEqualTo("REALTIME");
        assertThat(body.get("subject").asText()).isEqualTo("MERCHANT");
        assertThat(body.get("gradingMode").asText()).isEqualTo("SCORE_BASED");
        assertThat(body.get("status").asText()).isEqualTo("OFFLINE");
        assertThat(body.get("version").asInt()).isEqualTo(1);

        // 直接回查 MySQL：评级模型主体真实落库（R15.3）。
        IntegrationTestRows.RatingModelDetailRow row = testData.findRatingModelDetailById(modelId);
        assertThat(row.getName()).isEqualTo(name);
        assertThat(row.getEventTypeCode()).isEqualTo(eventCode);
        assertThat(row.getExecutionMode()).isEqualTo("REALTIME");
        assertThat(row.getSubject()).isEqualTo("MERCHANT");
        assertThat(row.getGradingMode()).isEqualTo("SCORE_BASED");
        assertThat(row.getStatus()).isEqualTo("OFFLINE");
        assertThat(row.getVersion()).isEqualTo(1);

        // 自动写入版本 1 快照（R10.6）：rating_model_version 恰有 1 行，版本号 1，快照非空。
        assertThat(versionCount(modelId)).isEqualTo(1);
        IntegrationTestRows.VersionSnapshotRow ver = testData.findRatingModelVersionSnapshot(modelId);
        assertThat(ver.getVersion()).isEqualTo(1);
        assertThat(ver.getSnapshotJson()).isNotBlank();
    }

    // —— R10.3 执行方式与评级主体枚举校验：非法取值被拒绝且不落库 ——

    @Test
    void invalidExecutionModeOrSubject_isRejected_andNotPersisted() {
        String eventCode = createEvent("场景-枚举校验", "枚举事件");

        // 非法执行方式（非 REALTIME/SCHEDULED）→ 字段级校验错误（R10.3）。
        String badModeName = MARKER + "MODEL_BADMODE_" + runId;
        ResponseEntity<String> badMode = postJson("/api/v1/rating-models",
                createBody(badModeName, eventCode, "HOURLY", "MERCHANT", "SCORE_BASED"));
        assertThat(badMode.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode modeErr = parse(badMode);
        assertThat(modeErr.get("code").asText()).isEqualTo("VALIDATION.INVALID_FIELD");
        assertThat(modeErr.get("fields").has("executionMode"))
                .as("应返回 executionMode 非法取值错误（R10.3）").isTrue();
        assertThat(countByName(badModeName)).as("非法执行方式不应落库（R10.3）").isZero();

        // 非法评级主体（非 MERCHANT/INDIVIDUAL）→ 字段级校验错误（R10.3）。
        String badSubjectName = MARKER + "MODEL_BADSUBJ_" + runId;
        ResponseEntity<String> badSubject = postJson("/api/v1/rating-models",
                createBody(badSubjectName, eventCode, "REALTIME", "ORG", "DIRECT"));
        assertThat(badSubject.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode subjErr = parse(badSubject);
        assertThat(subjErr.get("code").asText()).isEqualTo("VALIDATION.INVALID_FIELD");
        assertThat(subjErr.get("fields").has("subject"))
                .as("应返回 subject 非法取值错误（R10.3）").isTrue();
        assertThat(countByName(badSubjectName)).as("非法评级主体不应落库（R10.3）").isZero();
    }

    // —— R10.6 版本新建：PUT 保存创建新版本并保留历史 ——

    @Test
    void saveCreatesNewVersion_preservingHistory() {
        String eventCode = createEvent("场景-版本新建", "版本事件");
        long modelId = createModel(MARKER + "MODEL_SAVE_" + runId, eventCode, "SCHEDULED", "INDIVIDUAL", "SCORE_BASED");
        assertThat(versionCount(modelId)).as("创建后应有版本 1").isEqualTo(1);

        // 第一次保存编辑（3 个连续区间）→ 版本 2。
        ResponseEntity<String> save1 = putJson("/api/v1/rating-models/" + modelId,
                saveBody("保存名-2", "SCORE_BASED", contiguousBands(3)));
        assertThat(save1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(save1).get("version").asInt()).isEqualTo(2);
        assertThat(versionCount(modelId)).as("一次保存后版本数应递增到 2（R10.6）").isEqualTo(2);

        // 第二次保存编辑（4 个连续区间）→ 版本 3。
        ResponseEntity<String> save2 = putJson("/api/v1/rating-models/" + modelId,
                saveBody("保存名-3", "SCORE_BASED", contiguousBands(4)));
        assertThat(save2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(save2).get("version").asInt()).isEqualTo(3);
        assertThat(versionCount(modelId)).as("两次保存后版本数应递增到 3（R10.6）").isEqualTo(3);

        // 版本号单调递增 1→2→3，且历史快照全部仍可读回（snapshot_json 非空，R10.6）。
        List<IntegrationTestRows.VersionSnapshotRow> rows = testData.findRatingModelVersionSnapshots(modelId);
        assertThat(rows).hasSize(3);
        for (int i = 0; i < rows.size(); i++) {
            assertThat(rows.get(i).getVersion()).isEqualTo(i + 1);
            assertThat(rows.get(i).getSnapshotJson())
                    .as("历史版本 %d 快照应保留且可读回（R10.6）", i + 1)
                    .isNotBlank();
        }

        // rating_model 当前版本号为 3。
        Integer current = testData.findRatingModelVersionNumber(modelId);
        assertThat(current).isEqualTo(3);
    }

    // —— R10.5 版本历史查询：详情接口返回全部版本及版本号 ——

    @Test
    void versionHistory_returnsAllVersionsWithNumbers() {
        String eventCode = createEvent("场景-版本历史", "历史事件");
        long modelId = createModel(MARKER + "MODEL_HIST_" + runId, eventCode, "REALTIME", "MERCHANT", "SCORE_BASED");
        putJson("/api/v1/rating-models/" + modelId, saveBody("历史-2", "SCORE_BASED", contiguousBands(3)));
        putJson("/api/v1/rating-models/" + modelId, saveBody("历史-3", "SCORE_BASED", contiguousBands(3)));

        JsonNode detail = parse(getJson("/api/v1/rating-models/" + modelId));
        JsonNode versions = detail.get("versions");
        assertThat(versions.isArray()).isTrue();
        assertThat(versions.size()).isEqualTo(3);
        List<Integer> versionNumbers = new ArrayList<>();
        for (JsonNode v : versions) {
            assertThat(v.has("version")).isTrue();
            versionNumbers.add(v.get("version").asInt());
        }
        assertThat(versionNumbers).containsExactlyInAnyOrder(1, 2, 3);

        // 当前版本配置（源码页签）非空（R10.5）。
        assertThat(detail.get("sourceJson").asText()).isNotBlank();
    }

    // —— R10.7 上下线：状态切换并真实落库 ——

    @Test
    void onlineOffline_updatesStatus_andPersists() {
        String eventCode = createEvent("场景-上下线", "上下线事件");
        long modelId = createModel(MARKER + "MODEL_TOGGLE_" + runId, eventCode, "REALTIME", "MERCHANT", "DIRECT");
        assertThat(statusOf(modelId)).isEqualTo("OFFLINE");

        // 上线（R10.7）。
        ResponseEntity<String> online = postJson("/api/v1/rating-models/" + modelId + ":online", null);
        assertThat(online.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(online).get("status").asText()).isEqualTo("ONLINE");
        assertThat(statusOf(modelId)).as("上线后状态应真实落库为 ONLINE（R10.7）").isEqualTo("ONLINE");

        // 下线（R10.7）。
        ResponseEntity<String> offline = postJson("/api/v1/rating-models/" + modelId + ":offline", null);
        assertThat(offline.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(offline).get("status").asText()).isEqualTo("OFFLINE");
        assertThat(statusOf(modelId)).as("下线后状态应真实落库为 OFFLINE（R10.7）").isEqualTo("OFFLINE");
    }

    // —— R11.2 等级区间任意数量保存：5+ 连续区间保存成功并真实落库可读回（R15.3） ——

    @Test
    void saveManyContiguousBands_succeeds_andRoundTripsThroughRealMySql() {
        String eventCode = createEvent("场景-区间数量", "区间事件");
        long modelId = createModel(MARKER + "MODEL_BANDS_" + runId, eventCode, "REALTIME", "MERCHANT", "SCORE_BASED");

        int bandCount = 6;
        ResponseEntity<String> saved = putJson("/api/v1/rating-models/" + modelId,
                saveBody(null, "SCORE_BASED", contiguousBands(bandCount)));
        assertThat(saved.getStatusCode()).as("任意数量（6 个）连续区间应保存成功（R11.2）").isEqualTo(HttpStatus.OK);

        // 直接回查 MySQL：6 个等级区间真实落库且可重新读回（R15.3）。
        assertThat(gradeBandCount(modelId)).as("6 个等级区间应全部落库（R11.2/R15.3）").isEqualTo(bandCount);
        List<IntegrationTestRows.RatingGradeBandRow> bands = testData.findRatingGradeBandsByModelId(modelId);
        assertThat(bands).hasSize(bandCount);
        // 区间连续且界值与提交一致（[0,10],[10,20]...）。
        for (int i = 0; i < bandCount; i++) {
            assertThat(bands.get(i).getMinScore().intValue()).isEqualTo(i * 10);
            assertThat(bands.get(i).getMaxScore().intValue()).isEqualTo((i + 1) * 10);
            assertThat(bands.get(i).getGrade()).isEqualTo("G" + i);
        }

        // REST 详情再读回：等级区间一致（R15.3 往返）。
        JsonNode detail = parse(getJson("/api/v1/rating-models/" + modelId));
        JsonNode gradeBands = detail.get("model").get("gradeBands");
        assertThat(gradeBands.isArray()).isTrue();
        assertThat(gradeBands.size()).isEqualTo(bandCount);
    }

    // —— R11.4 区间重叠拦截：被拒绝（字段级错误）且不落库 ——

    @Test
    void overlappingBands_isRejected_andNotPersisted() {
        String eventCode = createEvent("场景-区间重叠", "重叠事件");
        long modelId = createModel(MARKER + "MODEL_OVERLAP_" + runId, eventCode, "REALTIME", "MERCHANT", "SCORE_BASED");

        // [0,10] 与 [5,20] 重叠（R11.4）。
        List<Map<String, Object>> bands = new ArrayList<>();
        bands.add(band(0, 10, "A", 0));
        bands.add(band(5, 20, "B", 1));

        ResponseEntity<String> resp = putJson("/api/v1/rating-models/" + modelId,
                saveBody(null, "SCORE_BASED", bands));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode err = parse(resp);
        assertThat(err.get("code").asText()).isEqualTo("VALIDATION.INVALID_FIELD");
        assertThat(err.get("fields").has("gradeBands.overlap"))
                .as("应返回区间重叠字段级错误（R11.4）").isTrue();

        // 不落库（R11.4）：保存被拒绝，等级区间为空、版本仍为 1。
        assertThat(gradeBandCount(modelId)).as("重叠区间不应落库（R11.4）").isZero();
        assertThat(versionCount(modelId)).as("保存失败不应新建版本（R11.4）").isEqualTo(1);
    }

    // —— R11.4 覆盖缺口拦截：被拒绝（字段级错误）且不落库 ——

    @Test
    void gapBands_isRejected_andNotPersisted() {
        String eventCode = createEvent("场景-覆盖缺口", "缺口事件");
        long modelId = createModel(MARKER + "MODEL_GAP_" + runId, eventCode, "REALTIME", "MERCHANT", "SCORE_BASED");

        // [0,10] 与 [20,30] 之间 (10,20) 无等级覆盖，存在缺口（R11.4）。
        List<Map<String, Object>> bands = new ArrayList<>();
        bands.add(band(0, 10, "A", 0));
        bands.add(band(20, 30, "B", 1));

        ResponseEntity<String> resp = putJson("/api/v1/rating-models/" + modelId,
                saveBody(null, "SCORE_BASED", bands));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode err = parse(resp);
        assertThat(err.get("code").asText()).isEqualTo("VALIDATION.INVALID_FIELD");
        assertThat(err.get("fields").has("gradeBands.gap"))
                .as("应返回区间覆盖缺口字段级错误（R11.4）").isTrue();

        // 不落库（R11.4）：保存被拒绝，等级区间为空、版本仍为 1。
        assertThat(gradeBandCount(modelId)).as("缺口区间不应落库（R11.4）").isZero();
        assertThat(versionCount(modelId)).as("保存失败不应新建版本（R11.4）").isEqualTo(1);
    }

    // —————————————————— 辅助方法 ——————————————————

    /** 经 REST 创建临时场景 + 事件，返回事件 code（评级模型创建要求所属事件真实存在，R14.2）。 */
    private String createEvent(String scenarioName, String eventName) {
        String scenarioCode = MARKER + "SCN_" + runId + "_" + System.nanoTime();
        ResponseEntity<String> scn = postJson("/api/v1/scenarios", Map.of(
                "code", scenarioCode, "name", scenarioName, "eventTypeCodes", List.of()));
        assertThat(scn.getStatusCode()).isEqualTo(HttpStatus.OK);
        long scenarioId = parse(scn).get("id").asLong();

        String eventCode = MARKER + "EVT_" + runId + "_" + System.nanoTime();
        ResponseEntity<String> evt = postJson("/api/v1/events", Map.of(
                "code", eventCode, "name", eventName, "scenarioId", scenarioId,
                "purposes", List.of("DECISION"), "eventKind", "FACT"));
        assertThat(evt.getStatusCode()).isEqualTo(HttpStatus.OK);
        return eventCode;
    }

    /** 经 REST 创建评级模型，返回其 id。 */
    private long createModel(String name, String eventCode, String executionMode,
                             String subject, String gradingMode) {
        ResponseEntity<String> resp = postJson("/api/v1/rating-models",
                createBody(name, eventCode, executionMode, subject, gradingMode));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(resp).get("id").asLong();
    }

    private Map<String, Object> createBody(String name, String eventCode, String executionMode,
                                           String subject, String gradingMode) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("eventTypeCode", eventCode);
        body.put("executionMode", executionMode);
        body.put("subject", subject);
        body.put("gradingMode", gradingMode);
        return body;
    }

    private Map<String, Object> saveBody(String name, String gradingMode, List<Map<String, Object>> gradeBands) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("gradingMode", gradingMode);
        body.put("gradeBands", gradeBands);
        body.put("items", List.of());
        return body;
    }

    /** 构造 n 个连续不重叠区间 [0,10],[10,20],...，grade 为 G0..G(n-1)。 */
    private List<Map<String, Object>> contiguousBands(int n) {
        List<Map<String, Object>> bands = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            bands.add(band(i * 10, (i + 1) * 10, "G" + i, i));
        }
        return bands;
    }

    private Map<String, Object> band(int min, int max, String grade, int orderNo) {
        Map<String, Object> b = new HashMap<>();
        b.put("minScore", new BigDecimal(min));
        b.put("maxScore", new BigDecimal(max));
        b.put("grade", grade);
        b.put("orderNo", orderNo);
        return b;
    }

    private int versionCount(long modelId) {
        Integer n = testData.countRatingModelVersionsByModelId(modelId);
        return n == null ? 0 : n;
    }

    private int gradeBandCount(long modelId) {
        Integer n = testData.countRatingGradeBandsByModelId(modelId);
        return n == null ? 0 : n;
    }

    private int countByName(String name) {
        Integer n = testData.countRatingModelByName(name);
        return n == null ? 0 : n;
    }

    private String statusOf(long modelId) {
        return testData.findRatingModelStatus(modelId);
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
