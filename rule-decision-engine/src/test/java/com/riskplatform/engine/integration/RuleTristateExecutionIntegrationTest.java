package com.riskplatform.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.rule.HitDecision;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinition;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinitionPort;
import com.riskplatform.engine.domain.rulepackage.RulePackageExecutor;
import com.riskplatform.engine.domain.rulepackage.RulePackageResult;
import com.riskplatform.engine.domain.rulepackage.TriggerMode;
import com.riskplatform.engine.integration.support.EngineIntegrationTestDataMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 规则三态执行集成测试（risk-console-redesign 任务 9.3，R7.9 / R7.10 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实引擎服务进程（{@code @SpringBootTest}，真实
 * Spring 上下文 + 真实 MyBatis-Plus 读 DAO + 真实 Aviator 求值）与<strong>真实 MySQL/Redis</strong>，
 * 经引擎真实加载链路 {@link RulePackageDefinitionPort}（{@code DbRulePackageDefinitionAdapter}）+
 * 真实执行器 {@link RulePackageExecutor}（{@code onlineRulePackageExecutor} bean）端到端验证规则三态
 * 语义（R7.3–R7.7）：
 * <ul>
 *   <li><b>下线规则不被执行</b>：OFFLINE 规则在加载阶段被过滤，既不进入执行定义，也不出现在执行结果
 *       命中明细中（R7.3/R7.4）。</li>
 *   <li><b>试运行规则被执行并在结果返回</b>：TRIAL_RUN 规则进入执行集，命中后出现在结果命中明细，且
 *       以 {@code trialRun=true} 标注（R7.5/R7.7）。</li>
 *   <li><b>试运行不影响最终决策聚合</b>：最终决策仅由 ONLINE 命中聚合得到；删去/加入试运行命中均不
 *       改变该决策（R7.6）。命中模式下决策聚合、评分模式下总分累加均隔离试运行。</li>
 *   <li><b>上线规则参与聚合</b>：ONLINE 命中参与最终决策聚合（命中模式）/总分累加（评分模式）。</li>
 * </ul>
 *
 * <p>数据经 {@link EngineIntegrationTestDataMapper} 直接种入引擎与 rule-config 共享的真实表（{@code rule_package} /
 * {@code rule_v2} / {@code rule_package_rule} / {@code rule_package_score_band}），并在执行前
 * <strong>直接回读 MySQL 断言数据真实落库可重新读回</strong>（R15.3）。
 *
 * <p>真实 MySQL/Redis 不可用时由 {@link AbstractEngineMySqlRedisIntegrationTest} 的前置校验
 * <strong>失败而非跳过</strong>（R15.2/R15.3）。
 *
 * <p>幂等/自清理：本测试仅创建以 {@value #MARKER} 前缀命名的临时数据，并在每个用例前后按前缀清理
 * （遵循逻辑外键依赖：先关联/子表后主体表），绝不污染既有种子数据。
 */
class RuleTristateExecutionIntegrationTest extends AbstractEngineMySqlRedisIntegrationTest {

    /** 临时数据命名前缀：保证与种子数据隔离，便于按前缀幂等清理。 */
    private static final String MARKER = "ZZIT_";

    @Autowired
    private EngineIntegrationTestDataMapper testData;

    /** 引擎在线规则包加载适配器（仅加载 ONLINE/TRIAL_RUN，跳过 OFFLINE，R7.3/R7.4）。 */
    @Autowired
    private RulePackageDefinitionPort rulePackageDefinitionPort;

    /** 引擎在线规则包执行器（决策聚合/总分累加仅纳入 ONLINE，R7.6）。 */
    @Autowired
    @Qualifier("onlineRulePackageExecutor")
    private RulePackageExecutor rulePackageExecutor;

    private String runId;

    @BeforeEach
    void setUp() {
        runId = Long.toString(System.nanoTime());
        cleanupMarkerData();
    }

    @AfterEach
    void tearDown() {
        cleanupMarkerData();
    }

    /** 按命名前缀幂等清理临时数据，顺序遵循逻辑外键依赖（先子表/关联后主体）。 */
    private void cleanupMarkerData() {
        testData.deleteRulePackageRulesByPackageCodePattern(MARKER + "%");
        testData.deleteRulePackageScoreBandsByPackageCodePattern(MARKER + "%");
        testData.deleteRulesByCodePattern(MARKER + "%");
        testData.deleteRulePackagesByCodePattern(MARKER + "%");
    }

    // ————————————————————————————————————————————————————————————————
    // 命中模式（HIT）：三态过滤 + 命中明细标注 + 决策聚合隔离
    // ————————————————————————————————————————————————————————————————

    @Test
    void hitMode_offlineSkipped_trialRunExecutedButIsolated_onlineDecides() {
        // —— 种子：HIT 规则包（启用）——
        long packageId = insertRulePackage(MARKER + "PKG_HIT_" + runId, "命中模式三态包", "HIT");

        // ONLINE 规则：命中，风险等级 MID → 决策 REVIEW；包内优先级 10（数值更大=更低优先级）
        long onlineRuleId = insertHitRule(MARKER + "R_ONLINE_" + runId, "amount >= 100", "MID", "ONLINE");
        bindRuleToPackage(packageId, onlineRuleId, 10);

        // TRIAL_RUN 规则：命中，风险等级 HIGH → 决策 REJECT；包内优先级 1（数值更小=更高优先级）。
        // 若该试运行命中被错误纳入聚合，最终决策将被其拉成 REJECT —— 用以放大隔离失败。
        long trialRuleId = insertHitRule(MARKER + "R_TRIAL_" + runId, "amount >= 100", "HIGH", "TRIAL_RUN");
        bindRuleToPackage(packageId, trialRuleId, 1);

        // OFFLINE 规则：表达式恒真本应命中，但下线后绝不应被执行
        long offlineRuleId = insertHitRule(MARKER + "R_OFFLINE_" + runId, "amount >= 0", "HIGH", "OFFLINE");
        bindRuleToPackage(packageId, offlineRuleId, 1);

        // —— R15.3：直接回读 MySQL 断言数据真实落库可重新读回 ——
        assertThat(countRulePackageRows(packageId)).isEqualTo(1);
        assertThat(readRuleStatus(onlineRuleId)).isEqualTo("ONLINE");
        assertThat(readRuleStatus(trialRuleId)).isEqualTo("TRIAL_RUN");
        assertThat(readRuleStatus(offlineRuleId)).isEqualTo("OFFLINE");
        assertThat(countPackageRuleRows(packageId)).isEqualTo(3);

        // —— 引擎真实加载链路：仅 ONLINE/TRIAL_RUN 进入执行定义，OFFLINE 被过滤（R7.3/R7.4）——
        RulePackageDefinition definition = rulePackageDefinitionPort.load(packageId);
        assertThat(definition).isNotNull();
        assertThat(definition.triggerMode()).isEqualTo(TriggerMode.HIT);
        Set<Long> loadedRuleIds = definition.rules().stream()
                .map(r -> r.ruleId()).collect(Collectors.toSet());
        assertThat(loadedRuleIds).containsExactlyInAnyOrder(onlineRuleId, trialRuleId);
        assertThat(loadedRuleIds).doesNotContain(offlineRuleId);

        // —— 真实执行 ——
        Map<String, Object> context = Map.of("amount", 500);
        RulePackageResult result = rulePackageExecutor.execute(definition, context);

        // 命中明细：包含上线 + 试运行命中，绝不包含下线规则（R7.5）
        Map<Long, HitDecision> hitsById = result.hitRules().stream()
                .collect(Collectors.toMap(HitDecision::ruleId, h -> h));
        assertThat(hitsById.keySet()).containsExactlyInAnyOrder(onlineRuleId, trialRuleId);
        assertThat(hitsById.keySet()).doesNotContain(offlineRuleId);

        // 三态标注正确（R7.7）：上线命中 trialRun=false，试运行命中 trialRun=true
        assertThat(hitsById.get(onlineRuleId).trialRun()).isFalse();
        assertThat(hitsById.get(trialRuleId).trialRun()).isTrue();

        // 最终决策仅由 ONLINE 命中聚合得到 = REVIEW（R7.6）。
        // 若错误纳入试运行（REJECT，优先级更高），结果会变成 REJECT —— 此处证明隔离生效。
        assertThat(result.decision()).isEqualTo(Decision.REVIEW);
    }

    // ————————————————————————————————————————————————————————————————
    // 评分模式（SCORE）：三态过滤 + 总分累加隔离试运行
    // ————————————————————————————————————————————————————————————————

    @Test
    void scoreMode_offlineSkipped_trialRunExecutedButNotScored_onlineAccumulates() {
        // —— 种子：SCORE 规则包（启用）——
        long packageId = insertRulePackage(MARKER + "PKG_SCORE_" + runId, "评分模式三态包", "SCORE");

        // 分值区间：[0,100) → LOW（PASS）；[100,+∞) → HIGH（REJECT）
        insertScoreBand(packageId, new BigDecimal("0"), new BigDecimal("100"), 1, 0, "LOW", 0);
        insertScoreBand(packageId, new BigDecimal("100"), null, 1, 0, "HIGH", 1);

        // ONLINE 评分规则：基础分 60，命中（恒真）→ 计入总分
        long onlineRuleId = insertScoreRule(MARKER + "S_ONLINE_" + runId, "amount >= 0", new BigDecimal("60"), "ONLINE");
        bindRuleToPackage(packageId, onlineRuleId, 1);

        // TRIAL_RUN 评分规则：基础分 1000，命中（恒真）→ 若被错误计入总分将跨入 HIGH 区间
        long trialRuleId = insertScoreRule(MARKER + "S_TRIAL_" + runId, "amount >= 0", new BigDecimal("1000"), "TRIAL_RUN");
        bindRuleToPackage(packageId, trialRuleId, 2);

        // OFFLINE 评分规则：基础分 9999，下线后绝不应被执行/计分
        long offlineRuleId = insertScoreRule(MARKER + "S_OFFLINE_" + runId, "amount >= 0", new BigDecimal("9999"), "OFFLINE");
        bindRuleToPackage(packageId, offlineRuleId, 3);

        // —— R15.3：直接回读 MySQL 断言数据真实落库可重新读回 ——
        assertThat(readRuleStatus(onlineRuleId)).isEqualTo("ONLINE");
        assertThat(readRuleStatus(trialRuleId)).isEqualTo("TRIAL_RUN");
        assertThat(readRuleStatus(offlineRuleId)).isEqualTo("OFFLINE");
        assertThat(countScoreBandRows(packageId)).isEqualTo(2);

        // —— 引擎真实加载链路：仅 ONLINE/TRIAL_RUN 进入执行定义，OFFLINE 被过滤（R7.3/R7.4）——
        RulePackageDefinition definition = rulePackageDefinitionPort.load(packageId);
        assertThat(definition).isNotNull();
        assertThat(definition.triggerMode()).isEqualTo(TriggerMode.SCORE);
        Set<Long> loadedRuleIds = definition.rules().stream()
                .map(r -> r.ruleId()).collect(Collectors.toSet());
        assertThat(loadedRuleIds).containsExactlyInAnyOrder(onlineRuleId, trialRuleId);
        assertThat(loadedRuleIds).doesNotContain(offlineRuleId);

        // —— 真实执行 ——
        Map<String, Object> context = Map.of("amount", 500);
        RulePackageResult result = rulePackageExecutor.execute(definition, context);

        // 命中（触发）明细：包含上线 + 试运行触发，绝不包含下线规则（R7.5）
        Map<Long, HitDecision> hitsById = result.hitRules().stream()
                .collect(Collectors.toMap(HitDecision::ruleId, h -> h));
        assertThat(hitsById.keySet()).containsExactlyInAnyOrder(onlineRuleId, trialRuleId);
        assertThat(hitsById.keySet()).doesNotContain(offlineRuleId);
        assertThat(hitsById.get(onlineRuleId).trialRun()).isFalse();
        assertThat(hitsById.get(trialRuleId).trialRun()).isTrue();

        // 总分仅累加 ONLINE 触发分 = 60（试运行 1000 与下线 9999 均不计入，R7.6）
        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("60"));
        // 总分 60 落入 [0,100) → 风险等级 LOW → 决策 PASS
        assertThat(result.riskLevelCode()).isEqualTo("LOW");
        assertThat(result.decision()).isEqualTo(Decision.PASS);
    }

    // —————————————————— 种子辅助（直接写真实共享表） ——————————————————

    /** 插入规则包（启用），返回自增主键。 */
    private long insertRulePackage(String code, String name, String triggerMode) {
        testData.insertRulePackage(code, name, triggerMode);
        Long id = testData.findRulePackageIdByCode(code);
        assertThat(id).as("种子数据应真实落库并可读回主键: " + code).isNotNull();
        return id;
    }

    /** 插入命中规则（rule_kind=HIT），返回自增主键。 */
    private long insertHitRule(String code, String compiledExpr, String riskLevelCode, String status) {
        testData.insertHitRule(code, riskLevelCode, compiledExpr, status);
        Long id = testData.findRuleIdByCode(code);
        assertThat(id).as("种子数据应真实落库并可读回主键: " + code).isNotNull();
        return id;
    }

    /** 插入评分规则（rule_kind=SCORE，含基础分），返回自增主键。 */
    private long insertScoreRule(String code, String compiledExpr, BigDecimal baseScore, String status) {
        testData.insertScoreRule(code, baseScore, compiledExpr, status);
        Long id = testData.findRuleIdByCode(code);
        assertThat(id).as("种子数据应真实落库并可读回主键: " + code).isNotNull();
        return id;
    }

    /** 绑定规则到规则包（含包内优先级）。 */
    private void bindRuleToPackage(long packageId, long ruleId, int priority) {
        testData.bindRuleToPackage(packageId, ruleId, priority);
    }

    /** 插入评分模式分值区间。 */
    private void insertScoreBand(long packageId, BigDecimal lower, BigDecimal upper,
                                 int lowerInclusive, int upperInclusive, String riskLevelCode, int orderNo) {
        testData.insertScoreBand(packageId, lower, upper, lowerInclusive, upperInclusive, riskLevelCode, orderNo);
    }

    // —————————————————— 回读辅助（断言真实落库 R15.3） ——————————————————

    private String readRuleStatus(long ruleId) {
        return testData.findRuleStatusById(ruleId);
    }

    private int countRulePackageRows(long packageId) {
        Integer n = testData.countRulePackageById(packageId);
        return n == null ? 0 : n;
    }

    private int countPackageRuleRows(long packageId) {
        Integer n = testData.countPackageRulesByPackageId(packageId);
        return n == null ? 0 : n;
    }

    private int countScoreBandRows(long packageId) {
        Integer n = testData.countScoreBandsByPackageId(packageId);
        return n == null ? 0 : n;
    }
}
