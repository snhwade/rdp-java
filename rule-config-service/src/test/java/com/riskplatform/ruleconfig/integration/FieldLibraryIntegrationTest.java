package com.riskplatform.ruleconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.field.FieldDefinition;
import com.riskplatform.ruleconfig.domain.field.FieldRepository;
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
 * 字段库（全局字段）模块集成测试（risk-console-redesign 任务 3.4，R3.9 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实服务进程（{@code @SpringBootTest} RANDOM_PORT，
 * 真实 Spring 上下文 + Flyway 迁移）与<strong>真实 MySQL</strong>，经真实 REST/AppService 全栈验证：
 * <ul>
 *   <li>创建字段（含受支持数据类型）—— R3.2/R3.3</li>
 *   <li>编辑字段 —— R3.5</li>
 *   <li>字段 code 真实重复时拒绝（{@code BUSINESS.DUPLICATE}）—— R3.4</li>
 *   <li><strong>关键反例</strong>：互为前缀的相似但非重复 code 不被误判为重复（R3.4 不误判）—— 两条 code 均成功落库</li>
 *   <li>批量导入混合有效/无效记录：持久化条数 = 有效条数，且逐条返回失败原因 —— R3.6</li>
 *   <li>关联关系查询：返回引用该字段的事件与衍生字段 —— R3.7</li>
 * </ul>
 * 每步均断言数据<strong>真实写入 MySQL 并可重新读回</strong>（经 {@link FieldRepository} 与
 * {@link IntegrationTestDataMapper} 直接回查，R15.3）。
 *
 * <p>真实 MySQL 不可用时由 {@link AbstractMySqlIntegrationTest} 的前置校验<strong>失败而非跳过</strong>
 * （R15.2/R15.3）。
 *
 * <p>幂等/可重复：本测试仅创建以 {@value #MARKER} 前缀命名的临时草稿数据（字段/衍生字段），
 * 并在每个用例前后按前缀清理，绝不污染既有种子数据。
 */
class FieldLibraryIntegrationTest extends AbstractMySqlIntegrationTest {

    /** 临时数据命名前缀：保证与种子数据隔离，便于按前缀幂等清理。 */
    private static final String MARKER = "ZZIT_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FieldRepository fieldRepository;

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

    /** 按命名前缀幂等清理临时数据。 */
    private void cleanupMarkerData() {
        testData.deleteDerivedFieldsByPattern(MARKER + "%", MARKER + "%");
        testData.deleteFieldLibraryByCodeOrNamePattern(MARKER + "%", MARKER + "%");
    }

    // —— R3.2/R3.3 创建 + R3.5 编辑：全链路往返落库 ——

    @Test
    void create_edit_roundTripsThroughRealMySql() {
        String code = MARKER + "FLD_CRUD_" + runId;

        // —— 创建（R3.2/R3.3）——
        ResponseEntity<String> created = postJson("/api/v1/fields", Map.of(
                "code", code,
                "name", MARKER + "交易金额",
                "dataType", "Double",
                "label", "单笔交易金额"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long fieldId = parse(created).get("id").asLong();
        assertThat(fieldId).isPositive();

        // 直接回查 MySQL：数据真实落库且可重新读回（R15.3）。
        FieldDefinition persisted = fieldRepository.findFieldById(fieldId).orElseThrow();
        assertThat(persisted.code()).isEqualTo(code);
        assertThat(persisted.name()).isEqualTo(MARKER + "交易金额");
        assertThat(persisted.dataType()).isEqualTo("Double");
        assertThat(countFieldRows(code)).isEqualTo(1);

        // —— 编辑（R3.5）：改名、改类型 ——
        ResponseEntity<String> edited = putJson("/api/v1/fields/" + fieldId, Map.of(
                "code", code,
                "name", MARKER + "交易金额-改",
                "dataType", "Integer",
                "label", "修改后含义",
                "enabled", true));
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);

        FieldDefinition afterEdit = fieldRepository.findFieldById(fieldId).orElseThrow();
        assertThat(afterEdit.name()).isEqualTo(MARKER + "交易金额-改");
        assertThat(afterEdit.dataType()).isEqualTo("Integer");
        assertThat(afterEdit.code()).isEqualTo(code);
        // 编辑不应产生新行。
        assertThat(countFieldRows(code)).isEqualTo(1);
    }

    // —— R3.4 字段 code 真实重复时拒绝 ——

    @Test
    void duplicateCode_isRejected_andNotPersistedTwice() {
        String code = MARKER + "FLD_DUP_" + runId;
        Map<String, Object> body = Map.of(
                "code", code,
                "name", MARKER + "重复字段",
                "dataType", "String",
                "label", "");

        assertThat(postJson("/api/v1/fields", body).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> dup = postJson("/api/v1/fields", Map.of(
                "code", code,
                "name", MARKER + "重复字段2",
                "dataType", "String",
                "label", ""));
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(parse(dup).get("code").asText()).isEqualTo("BUSINESS.DUPLICATE");

        // 仅落库一条（R3.4：真实重复时拒绝且不持久化）。
        assertThat(countFieldRows(code)).isEqualTo(1);
    }

    // —— R3.4 关键反例：互为前缀的相似 code 不被误判为重复 ——

    @Test
    void prefixSimilarCodes_areNotMisjudgedAsDuplicate() {
        // 构造互为前缀的相似但不同的两个 code：codeB 以 codeA 为严格前缀。
        String codeA = MARKER + "AMOUNT_" + runId;
        String codeB = codeA + "_TOTAL";
        assertThat(codeB).startsWith(codeA);
        assertThat(codeB).isNotEqualTo(codeA);

        ResponseEntity<String> a = postJson("/api/v1/fields", Map.of(
                "code", codeA, "name", MARKER + "金额", "dataType", "Double", "label", ""));
        assertThat(a.getStatusCode()).isEqualTo(HttpStatus.OK);

        // codeB 含 codeA 为前缀，但二者不相等 —— 绝不应被判为重复（R3.4 不误判）。
        ResponseEntity<String> b = postJson("/api/v1/fields", Map.of(
                "code", codeB, "name", MARKER + "金额合计", "dataType", "Double", "label", ""));
        assertThat(b.getStatusCode())
                .as("互为前缀的相似 code 不应被误判为重复（R3.4）")
                .isEqualTo(HttpStatus.OK);

        long idA = parse(a).get("id").asLong();
        long idB = parse(b).get("id").asLong();
        assertThat(idB).isNotEqualTo(idA);

        // 两条均真实落库且互相独立可读回。
        assertThat(fieldRepository.findFieldByCode(codeA).orElseThrow().code()).isEqualTo(codeA);
        assertThat(fieldRepository.findFieldByCode(codeB).orElseThrow().code()).isEqualTo(codeB);
        assertThat(countFieldRows(codeA)).isEqualTo(1);
        assertThat(countFieldRows(codeB)).isEqualTo(1);
    }

    // —— R3.6 批量导入：混合有效/无效记录，持久化数 = 有效数，逐条返回失败原因 ——

    @Test
    void importMixedRecords_persistsValidOnly_andReportsPerRecordFailures() {
        String validA = MARKER + "IMP_A_" + runId;
        String validB = MARKER + "IMP_B_" + runId;
        String invalidType = MARKER + "IMP_BADTYPE_" + runId;

        // 4 条记录：2 条有效；1 条数据类型不受支持（无效）；1 条缺失 code（无效）。
        List<Map<String, Object>> records = List.of(
                Map.of("code", validA, "name", MARKER + "有效A", "dataType", "String", "label", ""),
                Map.of("code", invalidType, "name", MARKER + "类型无效", "dataType", "Money", "label", ""),
                Map.of("code", "", "name", MARKER + "缺码", "dataType", "String", "label", ""),
                Map.of("code", validB, "name", MARKER + "有效B", "dataType", "Boolean", "label", ""));

        ResponseEntity<String> resp = postJson("/api/v1/fields/import", Map.of("records", records));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = parse(resp);

        JsonNode imported = body.get("imported");
        JsonNode failures = body.get("failures");
        assertThat(imported.isArray()).isTrue();
        assertThat(failures.isArray()).isTrue();

        // 持久化条数 == 有效记录条数（R3.6）。
        assertThat(imported.size()).isEqualTo(2);
        // 每条无效记录均返回失败原因（R3.6）。
        assertThat(failures.size()).isEqualTo(2);
        for (JsonNode f : failures) {
            assertThat(f.get("reason").asText()).isNotBlank();
        }

        // 真实落库校验：有效 code 各 1 行，无效 code 0 行（R15.3）。
        assertThat(countFieldRows(validA)).isEqualTo(1);
        assertThat(countFieldRows(validB)).isEqualTo(1);
        assertThat(countFieldRows(invalidType)).isZero();
        assertThat(fieldRepository.findFieldByCode(validA)).isPresent();
        assertThat(fieldRepository.findFieldByCode(validB)).isPresent();
        assertThat(fieldRepository.findFieldByCode(invalidType)).isEmpty();
    }

    // —— R3.7 关联关系查询：引用该字段的事件与衍生字段 ——

    @Test
    void relations_returnReferencingEventsAndDerivedFields() {
        String code = MARKER + "FLD_REL_" + runId;
        long fieldId = parse(postJson("/api/v1/fields", Map.of(
                "code", code, "name", MARKER + "被引用字段", "dataType", "Double", "label", "")))
                .get("id").asLong();

        // 创建一个衍生字段，其表达式引用该字段 code —— 构成真实的关联引用（R3.7）。
        String eventCode = MARKER + "EVT_REL_" + runId;
        ResponseEntity<String> derived = postJson("/api/v1/derived-fields", Map.of(
                "eventTypeCode", eventCode,
                "name", MARKER + "金额翻倍",
                "expression", code + " * 2"));
        assertThat(derived.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 关联关系查询应返回引用该字段的事件与衍生字段。
        JsonNode relations = parse(getJson("/api/v1/fields/" + fieldId + "/relations"));
        assertThat(relations.get("fieldId").asLong()).isEqualTo(fieldId);
        assertThat(relations.get("fieldCode").asText()).isEqualTo(code);

        List<String> derivedNames = relations.get("derivedFields").findValuesAsText("name");
        assertThat(derivedNames).contains(MARKER + "金额翻倍");

        // events 为字符串数组：逐元素提取文本。
        List<String> eventCodes = new java.util.ArrayList<>();
        relations.get("events").forEach(n -> eventCodes.add(n.asText()));
        assertThat(eventCodes).contains(eventCode);

        // 衍生字段真实落库可读回（R15.3）。
        assertThat(fieldRepository.listDerived(eventCode).stream().anyMatch(d -> (MARKER + "金额翻倍").equals(d.name())))
                .isTrue();
    }

    // —————————————————— 辅助方法 ——————————————————

    private int countFieldRows(String code) {
        Integer n = testData.countFieldLibraryByCode(code);
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
