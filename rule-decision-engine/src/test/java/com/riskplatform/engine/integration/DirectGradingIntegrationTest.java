package com.riskplatform.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.riskplatform.engine.domain.rating.DirectGrader;
import com.riskplatform.engine.domain.rating.DirectGradingItem;
import com.riskplatform.engine.domain.rating.DirectGradingResult;
import com.riskplatform.engine.domain.rating.GradeBand;
import com.riskplatform.engine.domain.rating.GradeOrder;
import com.riskplatform.engine.domain.rule.RuleExpressionEvaluator;
import com.riskplatform.engine.integration.support.EngineIntegrationTestDataMapper;
import com.riskplatform.engine.integration.support.EngineIntegrationTestRows;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 直接定级集成测试（risk-console-redesign 任务 15.5，R13.7 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实引擎服务进程（{@code @SpringBootTest}，真实
 * Spring 上下文 + 真实 Aviator 求值器 bean {@link RuleExpressionEvaluator}）与<strong>真实
 * MySQL/Redis</strong>，端到端验证直接定级（直接定级器 {@link DirectGrader}，R13.2–R13.7）：
 * <ul>
 *   <li><b>单项命中</b>：仅命中一项 → 该项等级（R13.2）。</li>
 *   <li><b>多项同级命中</b>：命中多项且等级相同 → 该等级（R13.3）。</li>
 *   <li><b>多项不同级取最高</b>：命中多项且等级不同 → 依据等级序 {@link GradeOrder} 取最高等级（R13.4）。</li>
 *   <li><b>未命中未定级</b>：未命中任何定级项 → 未定级（{@link DirectGrader#UNGRADED}，R13.5）。</li>
 *   <li><b>命中明细返回</b>：结果返回所得等级与全部命中定级项（R13.6）。</li>
 *   <li><b>定级结果可复现</b>：相同输入两次执行得到完全一致的结果（R13.7）。</li>
 * </ul>
 *
 * <p>数据经 {@link EngineIntegrationTestDataMapper} 直接种入引擎与 rule-config 共享的真实评级模型表
 * （{@code rating_model} / {@code rating_grade_band} / {@code rating_item}，直接定级项以
 * {@code condition_expr} + {@code grade} 承载），随后<strong>从 MySQL 真实加载</strong>等级区间
 * （构建 {@link GradeOrder}）与定级项并经真实 Aviator 求值器执行直接定级，执行前并
 * <strong>直接回读 MySQL 断言数据真实落库可重新读回</strong>（R15.3）。
 *
 * <p>真实 MySQL/Redis 不可用时由 {@link AbstractEngineMySqlRedisIntegrationTest} 的前置校验
 * <strong>失败而非跳过</strong>（R15.2/R15.3）。
 *
 * <p>幂等/自清理：本测试仅创建以 {@value #MARKER} 前缀命名的临时数据（与并行运行的评分定级集成测试
 * 任务 15.4 的前缀 {@code ZZIT_SBG_} 不同，避免冲突），并在每个用例前后按前缀清理（遵循逻辑外键依赖：
 * 先子表后主体表），绝不污染既有种子数据。
 */
class DirectGradingIntegrationTest extends AbstractEngineMySqlRedisIntegrationTest {

    /** 临时数据命名前缀：保证与种子数据及并行的评分定级测试（ZZIT_SBG_）隔离，便于按前缀幂等清理。 */
    private static final String MARKER = "ZZIT_DG_";

    @Autowired
    private EngineIntegrationTestDataMapper testData;

    /** 引擎真实 Aviator 规则表达式求值器 bean（条件求值复用，与规则求值同构）。 */
    @Autowired
    private RuleExpressionEvaluator ruleExpressionEvaluator;

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

    /** 按命名前缀幂等清理临时数据，顺序遵循逻辑外键依赖（先子表后主体表）。 */
    private void cleanupMarkerData() {
        testData.deleteRatingGradeBandsByModelNamePattern(MARKER + "%");
        testData.deleteRatingItemsByModelNamePattern(MARKER + "%");
        testData.deleteRatingModelsByNamePattern(MARKER + "%");
    }

    @Test
    void directGrading_singleHit_sameGrade_differentGradeHighest_noHit_andDeterminism() {
        // —— 种子：DIRECT 评级模型（对私·定时）——
        long modelId = insertRatingModel(MARKER + "MODEL_" + runId, "DIRECT", "SCHEDULED", "INDIVIDUAL");

        // 等级区间仅用于建立等级序（GradeOrder）：按 minScore 升序 三级 < 二级 < 一级
        insertGradeBand(modelId, bd(0), bd(30), "三级", 0);
        insertGradeBand(modelId, bd(30), bd(60), "二级", 1);
        insertGradeBand(modelId, bd(60), bd(100), "一级", 2);

        // 直接定级项（Aviator 布尔条件 + 命中等级）：
        //   A: amount >= 100                      → 二级
        //   B: amount >= 1000                     → 二级（与 A 同级）
        //   C: riskTags contains 'BLACKLIST'      → 一级（最高）
        //   D: amount >= 100000                   → 三级（用于校验未命中场景）
        insertDirectItem(modelId, "amount >= 100", "二级");
        insertDirectItem(modelId, "amount >= 1000", "二级");
        insertDirectItem(modelId, "string.contains(riskTags, 'BLACKLIST')", "一级");
        insertDirectItem(modelId, "amount >= 100000", "三级");

        // —— R15.3：直接回读 MySQL 断言数据真实落库可重新读回 ——
        assertThat(countModelRows(modelId)).isEqualTo(1);
        assertThat(countBandRows(modelId)).isEqualTo(3);
        assertThat(countItemRows(modelId)).isEqualTo(4);

        // —— 从 MySQL 真实加载等级区间（构建等级序）与直接定级项 ——
        List<GradeBand> bands = loadGradeBands(modelId);
        GradeOrder gradeOrder = GradeOrder.fromBands(bands);
        List<DirectGradingItem> items = loadDirectItems(modelId);
        assertThat(bands).hasSize(3);
        assertThat(items).hasSize(4);

        // —— 经真实 Aviator 求值器执行直接定级 ——
        DirectGrader grader = new DirectGrader(ruleExpressionEvaluator);

        // 场景 1 —— 单项命中（R13.2）：amount=500 → 仅 A 命中（>=100），B/C/D 均不命中 → 二级
        DirectGradingResult single = grader.grade(items, gradeOrder, Map.of("amount", 500, "riskTags", ""));
        assertThat(single.graded()).isTrue();
        assertThat(single.grade()).isEqualTo("二级");
        assertThat(single.hitItems()).hasSize(1);

        // 场景 2 —— 多项同级命中（R13.3）：amount=5000 → A、B 命中（同为二级），C/D 不命中 → 二级
        DirectGradingResult sameGrade = grader.grade(items, gradeOrder, Map.of("amount", 5000, "riskTags", ""));
        assertThat(sameGrade.graded()).isTrue();
        assertThat(sameGrade.grade()).isEqualTo("二级");
        assertThat(sameGrade.hitItems()).hasSize(2);
        assertThat(sameGrade.hitItems()).allSatisfy(it -> assertThat(it.grade()).isEqualTo("二级"));

        // 场景 3 —— 多项不同级取最高（R13.4 / 命中明细 R13.6）：amount=5000 且命中黑名单
        //   → A、B（二级）与 C（一级）命中，依据等级序取最高 → 一级；命中明细含全部 3 项
        Map<String, Object> mixedContext = Map.of("amount", 5000, "riskTags", "VIP,BLACKLIST");
        DirectGradingResult highest = grader.grade(items, gradeOrder, mixedContext);
        assertThat(highest.graded()).isTrue();
        assertThat(highest.grade()).isEqualTo("一级");
        assertThat(highest.hitItems()).hasSize(3);
        assertThat(highest.hitItems()).extracting(DirectGradingItem::grade)
                .containsExactlyInAnyOrder("二级", "二级", "一级");

        // 场景 4 —— 未命中未定级（R13.5）：amount=10 且无黑名单 → 无命中 → 未定级
        DirectGradingResult none = grader.grade(items, gradeOrder, Map.of("amount", 10, "riskTags", ""));
        assertThat(none.graded()).isFalse();
        assertThat(none.grade()).isEqualTo(DirectGrader.UNGRADED);
        assertThat(none.hitItems()).isEmpty();

        // —— 定级结果可复现（R13.7）：相同输入二次执行（重新从 MySQL 加载）得到完全一致结果 ——
        DirectGradingResult highestAgain = grader.grade(
                loadDirectItems(modelId), GradeOrder.fromBands(loadGradeBands(modelId)), mixedContext);
        assertThat(highestAgain.graded()).isEqualTo(highest.graded());
        assertThat(highestAgain.grade()).isEqualTo(highest.grade());
        assertThat(highestAgain.hitItems()).hasSize(highest.hitItems().size());
        assertThat(highestAgain.hitItems()).extracting(DirectGradingItem::grade)
                .containsExactlyElementsOf(highest.hitItems().stream().map(DirectGradingItem::grade).toList());
    }

    // —————————————————— 种子辅助（直接写真实共享表） ——————————————————

    /** 插入评级模型（上线、版本 1），返回自增主键。 */
    private long insertRatingModel(String name, String gradingMode, String executionMode, String subject) {
        testData.insertRatingModel(name, MARKER + "EVT", executionMode, subject, gradingMode);
        Long id = testData.findRatingModelIdByName(name);
        assertThat(id).as("种子数据应真实落库并可读回主键: " + name).isNotNull();
        return id;
    }

    /** 插入等级区间（用于建立等级序）。 */
    private void insertGradeBand(long modelId, BigDecimal minScore, BigDecimal maxScore, String grade, int orderNo) {
        testData.insertGradeBand(modelId, minScore, maxScore, grade, orderNo);
    }

    /** 插入直接定级项（直接定级：condition_expr + grade，无分值/上限）。 */
    private void insertDirectItem(long modelId, String condition, String grade) {
        testData.insertDirectRatingItem(modelId, condition, grade);
    }

    // —————————————————— 加载辅助（从 MySQL 真实读回 R15.3） ——————————————————

    /** 从 MySQL 加载等级区间，按 order_no 升序。 */
    private List<GradeBand> loadGradeBands(long modelId) {
        return testData.findGradeBandsByModelId(modelId).stream()
                .map((EngineIntegrationTestRows.GradeBandRow row) ->
                        new GradeBand(row.getMinScore(), row.getMaxScore(), row.getGrade()))
                .toList();
    }

    /** 从 MySQL 加载直接定级项，按 id 升序保证可复现的稳定顺序。 */
    private List<DirectGradingItem> loadDirectItems(long modelId) {
        return testData.findDirectItemsByModelId(modelId).stream()
                .map((EngineIntegrationTestRows.DirectItemRow row) ->
                        new DirectGradingItem(row.getConditionExpr(), row.getGrade()))
                .toList();
    }

    // —————————————————— 回读辅助（断言真实落库 R15.3） ——————————————————

    private int countModelRows(long modelId) {
        Integer n = testData.countRatingModelById(modelId);
        return n == null ? 0 : n;
    }

    private int countBandRows(long modelId) {
        Integer n = testData.countGradeBandsByModelId(modelId);
        return n == null ? 0 : n;
    }

    private int countItemRows(long modelId) {
        Integer n = testData.countRatingItemsByModelId(modelId);
        return n == null ? 0 : n;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
