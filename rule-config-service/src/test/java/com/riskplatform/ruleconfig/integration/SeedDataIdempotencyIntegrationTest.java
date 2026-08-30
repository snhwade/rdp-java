package com.riskplatform.ruleconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.infrastructure.security.JwtService;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestDataMapper;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestRows;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 迁移与种子幂等集成测试（risk-console-redesign 任务 16.2，
 * R14.3 / R15.5 / R15.6 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实服务进程（{@code @SpringBootTest} RANDOM_PORT，
 * 真实 Spring 上下文 + Flyway 全量迁移 V1..V25 + 可重复种子 {@code R__seed_*.sql}）与
 * <strong>真实 MySQL/Redis</strong>，经真实 REST/AppService 全栈验证：
 *
 * <ul>
 *   <li><strong>种子数据首登可见非空（R15.5）</strong>：四大模块「首次登录」页面经真实 REST 拉取，
 *       断言种子数据非空——
 *       <ul>
 *         <li>参数管理：场景树 ≥2 业务场景、事件 ≥4、字段库 ≥8、验证策略 ≥3（含不限场景）；</li>
 *         <li>规则管理：规则包 ≥2，其下规则三态俱全（ONLINE + TRIAL_RUN + OFFLINE）；</li>
 *         <li>决策流：≥1 决策流且存在一个 ONLINE 版本；</li>
 *         <li>评级模型：≥2，含 SCORE_BASED 与 DIRECT 各一。</li>
 *       </ul></li>
 *   <li><strong>迁移/种子幂等（R14.3/R15.6）</strong>：从 classpath {@code db/migration} 重新执行
 *       全部 {@code R__seed_*.sql}，断言种子记录数<strong>不变</strong>、无重复业务主键；</li>
 *   <li><strong>schema 不变（R14.3/R15.6）</strong>：重复执行种子前后，相关表的列结构签名
 *       （列名 + 类型）完全一致；</li>
 *   <li><strong>数据可重新读回（R15.3）</strong>：种子数据经 {@link IntegrationTestDataMapper} 直接回查 MySQL
 *       且与稳定业务编码一致。</li>
 * </ul>
 *
 * <p>真实 MySQL/Redis 不可用时由 {@link AbstractMySqlRedisIntegrationTest} 的前置校验
 * <strong>失败而非跳过</strong>（R15.2/R15.3）。
 *
 * <p><strong>幂等/共享基线约定</strong>：种子数据是四大模块的共享基线，
 * 本测试<strong>绝不删除任何种子数据</strong>，仅以稳定业务编码断言并安全地重复执行
 * 幂等种子脚本（重复执行不产生脏数据）。
 */
class SeedDataIdempotencyIntegrationTest extends AbstractMySqlRedisIntegrationTest {

    /** 可重复种子脚本（classpath db/migration），与 Flyway 实际执行的脚本同源。 */
    private static final List<String> SEED_SCRIPTS = List.of(
            "db/migration/R__seed_param_management.sql",
            "db/migration/R__seed_rules.sql",
            "db/migration/R__seed_flows.sql",
            "db/migration/R__seed_rating.sql");

    /** 种子预置的稳定业务编码（用于按编码精确断言，隔离其它测试数据）。 */
    private static final List<String> SEED_SCENARIO_CODES = List.of("SCN_PAYMENT", "SCN_SETTLE");
    private static final List<String> SEED_EVENT_CODES = List.of(
            "EVT_PAY_APPLY", "EVT_PAY_RESULT", "EVT_MERCHANT_PROFILE", "EVT_SETTLE_APPLY", "EVT_ACCOUNT_DIM");
    private static final List<String> SEED_FIELD_CODES = List.of(
            "txn_amount", "txn_currency", "merchant_id", "txn_count_1d", "is_cross_border",
            "account_open_date", "risk_score", "card_bin", "last_txn_time", "device_age_days");
    private static final List<String> SEED_VERIFY_CODES = List.of("VFY_OTP_SMS", "VFY_FACE", "VFY_DEVICE");
    private static final List<String> SEED_RULE_PACKAGE_CODES = List.of("PKG_PAY_HIT", "PKG_PAY_SCORE");
    private static final List<String> SEED_RULE_CODES = List.of(
            "RULE_PAY_BIGAMT", "RULE_PAY_CROSSBORDER", "RULE_PAY_LEGACY",
            "RULE_SCORE_HIGHFREQ", "RULE_SCORE_NEWDEVICE");
    private static final List<String> SEED_RATING_MODEL_NAMES = List.of("商户实时风险评级", "对私定时风险评级");

    /** 重复执行种子前后需断言 schema 不变的相关表。 */
    private static final List<String> SCHEMA_TABLES = List.of(
            "scenario", "scenario_event", "event_type", "field_library", "event_field",
            "strategy_def", "rule_package", "rule_package_event", "rule_v2", "rule_package_rule",
            "decision_flow", "decision_flow_version",
            "rating_model", "rating_grade_band", "rating_item", "rating_model_version");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private IntegrationTestDataMapper testData;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String token() {
        return jwtService.issue("it-seed", List.of("ADMIN"));
    }

    // —————————————————————————————————————————————————————————————————————
    // R15.5 四大模块首登页面：种子数据可见且非空（经真实 REST 全栈）
    // —————————————————————————————————————————————————————————————————————

    @Test
    void seedData_visibleAndNonEmpty_acrossFourModules() {
        // —— 参数管理：场景树 ≥2 业务场景 ——（R15.5）
        JsonNode tree = parse(get("/api/v1/scenarios/tree"));
        assertThat(tree.isArray()).isTrue();
        assertThat(tree.size()).as("场景树应展示 ≥2 业务场景（R15.5）").isGreaterThanOrEqualTo(2);

        // —— 参数管理：事件 ≥4 ——
        JsonNode events = parse(get("/api/v1/events"));
        assertThat(events.size()).as("事件列表应展示 ≥4 事件（R15.5）").isGreaterThanOrEqualTo(4);

        // —— 参数管理：字段库 ≥8 ——
        JsonNode fields = parse(get("/api/v1/fields"));
        assertThat(fields.size()).as("字段库应展示 ≥8 字段（R15.5）").isGreaterThanOrEqualTo(8);

        // —— 参数管理：验证策略 ≥3，且含一条不限业务场景的策略 ——
        JsonNode strategies = parse(get("/api/v1/verify-strategies"));
        assertThat(strategies.size()).as("验证策略应展示 ≥3 策略（R15.5）").isGreaterThanOrEqualTo(3);
        boolean hasAnyScope = false;
        for (JsonNode s : strategies) {
            if (s.path("anyScope").asBoolean(false)) {
                hasAnyScope = true;
                break;
            }
        }
        assertThat(hasAnyScope).as("应存在一条不限业务场景的验证策略（R15.5）").isTrue();

        // —— 规则管理：规则包 ≥2，其下规则三态俱全（ONLINE + TRIAL_RUN + OFFLINE）——
        JsonNode rulePackages = parse(get("/api/v1/rule-packages"));
        assertThat(rulePackages.size()).as("规则包卡片墙应展示 ≥2 规则包（R15.5）").isGreaterThanOrEqualTo(2);
        long seedOnline = 0;
        long seedTrialRun = 0;
        long seedOffline = 0;
        int seedPackageCount = 0;
        for (JsonNode p : rulePackages) {
            if (SEED_RULE_PACKAGE_CODES.contains(p.path("code").asText())) {
                seedPackageCount++;
                JsonNode counts = p.path("counts");
                seedOnline += counts.path("online").asLong();
                seedTrialRun += counts.path("trialRun").asLong();
                seedOffline += counts.path("offline").asLong();
            }
        }
        assertThat(seedPackageCount).as("两个种子规则包应可见（R15.5）").isEqualTo(2);
        assertThat(seedOnline).as("种子规则包应含上线规则（R15.5）").isGreaterThanOrEqualTo(1);
        assertThat(seedTrialRun).as("种子规则包应含试运行规则（R15.5）").isGreaterThanOrEqualTo(1);
        assertThat(seedOffline).as("种子规则包应含下线规则（R15.5）").isGreaterThanOrEqualTo(1);

        // —— 决策流：≥1 决策流，且存在一个 ONLINE 版本 ——
        JsonNode flows = parse(get("/api/v1/decision-flows"));
        assertThat(flows.size()).as("决策流列表应展示 ≥1 决策流（R15.5）").isGreaterThanOrEqualTo(1);
        boolean anyFlowHasOnlineVersion = false;
        for (JsonNode flow : flows) {
            long flowId = flow.path("id").asLong();
            JsonNode versions = parse(get("/api/v1/decision-flows/" + flowId + "/versions"));
            for (JsonNode v : versions) {
                if ("ONLINE".equals(v.path("status").asText())) {
                    anyFlowHasOnlineVersion = true;
                    break;
                }
            }
            if (anyFlowHasOnlineVersion) {
                break;
            }
        }
        assertThat(anyFlowHasOnlineVersion).as("应存在一个含 ONLINE 版本的决策流（R15.5）").isTrue();

        // —— 评级模型：≥2，含 SCORE_BASED 与 DIRECT 各一 ——
        JsonNode ratingModels = parse(get("/api/v1/rating-models"));
        assertThat(ratingModels.size()).as("评级模型卡片墙应展示 ≥2 评级模型（R15.5）").isGreaterThanOrEqualTo(2);
        boolean hasScoreBased = false;
        boolean hasDirect = false;
        for (JsonNode m : ratingModels) {
            String mode = m.path("gradingMode").asText();
            if ("SCORE_BASED".equals(mode)) {
                hasScoreBased = true;
            } else if ("DIRECT".equals(mode)) {
                hasDirect = true;
            }
        }
        assertThat(hasScoreBased).as("应存在评分定级（SCORE_BASED）评级模型（R15.5）").isTrue();
        assertThat(hasDirect).as("应存在直接定级（DIRECT）评级模型（R15.5）").isTrue();
    }

    // —————————————————————————————————————————————————————————————————————
    // R15.3 种子数据真实落库且可重新读回（直接回查 MySQL）
    // —————————————————————————————————————————————————————————————————————

    @Test
    void seedData_reReadableFromRealMySql() {
        // 参数管理
        assertThat(countIn("scenario", "code", SEED_SCENARIO_CODES))
                .as("2 个种子业务场景应真实落库（R15.3）").isEqualTo(2);
        assertThat(countIn("event_type", "code", SEED_EVENT_CODES))
                .as("5 个种子事件应真实落库（R15.3）").isEqualTo(5);
        assertThat(countIn("field_library", "code", SEED_FIELD_CODES))
                .as("10 个种子字段应真实落库（R15.3）").isEqualTo(10);
        // 事件字段关联含 ≥1 衍生标记（R4.9）
        Integer derivedCount = testData.countDerivedEventFields();
        assertThat(derivedCount).as("应存在 ≥1 衍生事件字段（R15.3）").isNotNull().isGreaterThanOrEqualTo(1);
        assertThat(countIn("strategy_def", "code", SEED_VERIFY_CODES))
                .as("3 个种子验证策略应真实落库（R15.3）").isEqualTo(3);

        // 规则管理
        assertThat(countIn("rule_package", "code", SEED_RULE_PACKAGE_CODES))
                .as("2 个种子规则包应真实落库（R15.3）").isEqualTo(2);
        assertThat(countIn("rule_v2", "code", SEED_RULE_CODES))
                .as("5 条种子规则应真实落库（R15.3）").isEqualTo(5);
        // 三态俱全
        assertThat(ruleStatusCount("ONLINE")).as("种子规则含 ONLINE（R15.3）").isGreaterThanOrEqualTo(1);
        assertThat(ruleStatusCount("TRIAL_RUN")).as("种子规则含 TRIAL_RUN（R15.3）").isGreaterThanOrEqualTo(1);
        assertThat(ruleStatusCount("OFFLINE")).as("种子规则含 OFFLINE（R15.3）").isGreaterThanOrEqualTo(1);

        // 决策流：含一个 ONLINE 版本（R15.3）
        Integer onlineFlowVersions = testData.countOnlineDecisionFlowVersions();
        assertThat(onlineFlowVersions).as("应存在 ≥1 ONLINE 决策流版本（R15.3）").isNotNull().isGreaterThanOrEqualTo(1);

        // 评级模型：2 个，含 SCORE_BASED 与 DIRECT
        assertThat(countIn("rating_model", "name", SEED_RATING_MODEL_NAMES))
                .as("2 个种子评级模型应真实落库（R15.3）").isEqualTo(2);
        assertThat(ratingModeCount("SCORE_BASED")).as("种子评级模型含 SCORE_BASED（R15.3）").isGreaterThanOrEqualTo(1);
        assertThat(ratingModeCount("DIRECT")).as("种子评级模型含 DIRECT（R15.3）").isGreaterThanOrEqualTo(1);
    }

    // —————————————————————————————————————————————————————————————————————
    // R14.3 / R15.6 重复执行种子脚本后记录数不变、无重复业务主键
    // —————————————————————————————————————————————————————————————————————

    @Test
    void reExecutingSeedScripts_isIdempotent_rowCountsUnchanged_andNoDuplicates() {
        // 1) 重复执行前：捕获各种子表的当前记录总数。
        Map<String, Integer> before = captureRowCounts();

        // 2) 从 classpath 重新执行全部可重复种子脚本（幂等 upsert，安全可重复）。
        reExecuteSeedScripts();

        // 3) 重复执行后：记录数应完全不变（R15.6）。
        Map<String, Integer> after = captureRowCounts();
        assertThat(after)
                .as("重复执行种子脚本后各表记录数应保持不变（R14.3/R15.6）")
                .isEqualTo(before);

        // 4) 再执行一次，进一步确认多次重复执行仍不产生新增（强幂等）。
        reExecuteSeedScripts();
        Map<String, Integer> afterTwice = captureRowCounts();
        assertThat(afterTwice)
                .as("二次重复执行种子脚本后各表记录数仍应保持不变（R14.3/R15.6）")
                .isEqualTo(before);

        // 5) 无重复业务主键（R15.6）：按稳定业务编码分组，不存在任何编码出现多次。
        assertNoDuplicateBusinessKeys();
    }

    // —————————————————————————————————————————————————————————————————————
    // R14.3 / R15.6 重复执行种子脚本后 schema 不变（列结构签名一致）
    // —————————————————————————————————————————————————————————————————————

    @Test
    void schema_unchanged_afterReExecutingSeedScripts() {
        Map<String, String> schemaBefore = captureSchemaSignature();
        reExecuteSeedScripts();
        Map<String, String> schemaAfter = captureSchemaSignature();
        assertThat(schemaAfter)
                .as("重复执行种子脚本不应改变任何相关表的 schema（R14.3/R15.6）")
                .isEqualTo(schemaBefore);
    }

    // —————————————————————————————————————————————————————————————————————
    // 辅助方法
    // —————————————————————————————————————————————————————————————————————

    /** 从 classpath 顺序重新执行全部可重复种子脚本（与 Flyway 同源，幂等可重复）。 */
    private void reExecuteSeedScripts() {
        try (Connection connection = dataSource.getConnection()) {
            for (String script : SEED_SCRIPTS) {
                EncodedResource resource = new EncodedResource(
                        new ClassPathResource(script), StandardCharsets.UTF_8);
                ScriptUtils.executeSqlScript(connection, resource);
            }
        } catch (Exception e) {
            throw new IllegalStateException("重新执行种子脚本失败（R15.6 幂等验证）: " + e.getMessage(), e);
        }
    }

    /** 捕获各种子表的当前记录总数。 */
    private Map<String, Integer> captureRowCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : SCHEMA_TABLES) {
            counts.put(table, rowCountForTable(table));
        }
        return counts;
    }

    private int rowCountForTable(String table) {
        Integer n = switch (table) {
            case "scenario" -> testData.countScenarioRows();
            case "scenario_event" -> testData.countScenarioEventRows();
            case "event_type" -> testData.countEventTypeRows();
            case "field_library" -> testData.countFieldLibraryRows();
            case "event_field" -> testData.countEventFieldRows();
            case "strategy_def" -> testData.countStrategyDefRows();
            case "rule_package" -> testData.countRulePackageRows();
            case "rule_package_event" -> testData.countRulePackageEventRows();
            case "rule_v2" -> testData.countRuleV2Rows();
            case "rule_package_rule" -> testData.countRulePackageRuleRows();
            case "decision_flow" -> testData.countDecisionFlowRows();
            case "decision_flow_version" -> testData.countDecisionFlowVersionRows();
            case "rating_model" -> testData.countRatingModelRows();
            case "rating_grade_band" -> testData.countRatingGradeBandRows();
            case "rating_item" -> testData.countRatingItemRows();
            case "rating_model_version" -> testData.countRatingModelVersionRows();
            default -> throw new IllegalArgumentException("Unknown table: " + table);
        };
        return n == null ? 0 : n;
    }

    /** 捕获相关表的列结构签名（列名 + 数据类型 + 可空 + 默认值），用于断言 schema 不变。 */
    private Map<String, String> captureSchemaSignature() {
        Map<String, String> signature = new LinkedHashMap<>();
        for (String table : SCHEMA_TABLES) {
            List<IntegrationTestRows.ColumnMetaRow> cols = testData.findColumnMetaByTableName(table);
            StringBuilder sb = new StringBuilder();
            for (IntegrationTestRows.ColumnMetaRow col : cols) {
                sb.append(col.getColumnName()).append(':')
                        .append(col.getColumnType()).append(':')
                        .append(col.getIsNullable()).append(':')
                        .append(col.getColumnKey()).append('|');
            }
            signature.put(table, sb.toString());
        }
        return signature;
    }

    /** 断言种子各表按稳定业务编码不存在重复记录（GROUP BY ... HAVING COUNT(*)>1 为空）。 */
    private void assertNoDuplicateBusinessKeys() {
        assertNoDuplicate("scenario", "code", SEED_SCENARIO_CODES);
        assertNoDuplicate("event_type", "code", SEED_EVENT_CODES);
        assertNoDuplicate("field_library", "code", SEED_FIELD_CODES);
        assertNoDuplicate("strategy_def", "code", SEED_VERIFY_CODES);
        assertNoDuplicate("rule_package", "code", SEED_RULE_PACKAGE_CODES);
        assertNoDuplicate("rule_v2", "code", SEED_RULE_CODES);
        assertNoDuplicate("rating_model", "name", SEED_RATING_MODEL_NAMES);

        assertThat(testData.countDuplicateEventFieldGroups())
                .as("事件—字段关联不应出现重复（R15.6）").isZero();
        assertThat(testData.countDuplicateSeedDecisionFlowGroups())
                .as("种子决策流不应重复（R15.6）").isZero();
        assertThat(testData.countDuplicateDecisionFlowVersionGroups())
                .as("决策流版本不应重复（R15.6）").isZero();
        assertThat(testData.countDuplicateRatingModelVersionGroups())
                .as("评级模型版本不应重复（R15.6）").isZero();
    }

    /** 断言给定表的某业务编码列不存在重复（每个种子编码出现次数恰为 1）。 */
    private void assertNoDuplicate(String table, String keyColumn, List<String> codes) {
        List<IntegrationTestRows.DuplicateKeyRow> dups =
                testData.findDuplicateKeys(table, keyColumn, codes);
        assertThat(dups)
                .as("表 %s 的种子业务编码 %s 不应出现重复记录（R15.6）", table, keyColumn)
                .isEmpty();
    }

    /** 统计某表中业务编码列落在给定集合内的记录数。 */
    private int countIn(String table, String keyColumn, List<String> codes) {
        Integer n = switch (table) {
            case "scenario" -> testData.countScenariosByCodes(codes);
            case "event_type" -> testData.countEventTypesByCodes(codes);
            case "field_library" -> testData.countFieldLibraryByCodes(codes);
            case "strategy_def" -> testData.countStrategyDefByCodes(codes);
            case "rule_package" -> testData.countRulePackagesByCodes(codes);
            case "rule_v2" -> testData.countRulesByCodes(codes);
            case "rating_model" -> testData.countRatingModelsByNames(codes);
            default -> throw new IllegalArgumentException("Unknown table: " + table + ", column: " + keyColumn);
        };
        return n == null ? 0 : n;
    }

    private int ruleStatusCount(String status) {
        Integer n = testData.countSeedRulesByCodesAndStatus(SEED_RULE_CODES, status);
        return n == null ? 0 : n;
    }

    private int ratingModeCount(String gradingMode) {
        Integer n = testData.countSeedRatingModelsByNamesAndMode(SEED_RATING_MODEL_NAMES, gradingMode);
        return n == null ? 0 : n;
    }

    // —— HTTP 辅助（携带真实 JWT） ——

    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token());
        ResponseEntity<String> resp = restTemplate.exchange(
                "http://localhost:" + port + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(resp.getStatusCode()).as("GET %s 应成功（R15.1）", path).isEqualTo(HttpStatus.OK);
        return resp;
    }

    private JsonNode parse(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("解析响应体失败: " + response.getBody(), e);
        }
    }
}
