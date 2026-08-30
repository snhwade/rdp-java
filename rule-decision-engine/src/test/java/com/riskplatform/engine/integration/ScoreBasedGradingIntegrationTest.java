package com.riskplatform.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.riskplatform.engine.domain.rating.GradeBand;
import com.riskplatform.engine.domain.rating.RatingItem;
import com.riskplatform.engine.domain.rating.ScoreBasedGrader;
import com.riskplatform.engine.domain.rating.ScoreBasedRatingResult;
import com.riskplatform.engine.domain.rule.RuleExpressionEvaluator;
import com.riskplatform.engine.integration.support.EngineIntegrationTestDataMapper;
import com.riskplatform.engine.integration.support.EngineIntegrationTestRows;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 评分定级集成测试（risk-console-redesign 任务 15.4，R12.8 / R15.1 / R15.3）。
 *
 * <p><strong>硬性集成测试（非可选）</strong>：使用真实引擎服务进程（{@code @SpringBootTest}，真实
 * Spring 上下文 + 真实 Aviator 求值器 bean {@link RuleExpressionEvaluator}）与<strong>真实
 * MySQL/Redis</strong>，端到端验证评分定级（评分定级器 {@link ScoreBasedGrader}，R12.2–R12.8）：
 * <ul>
 *   <li><b>子项分值累加</b>：各命中评级子项计入分值之和为总分（R12.3）。</li>
 *   <li><b>子项分值上限封顶</b>：计入分值 = min(分值, 子项分值上限)（R12.2）。</li>
 *   <li><b>总分落入区间得到等级</b>：总分落入唯一等级区间得对应等级（R12.4）。</li>
 *   <li><b>命中明细返回</b>：结果返回总分、所得等级与各命中子项及其计入分值（R12.7）。</li>
 *   <li><b>定级结果可复现</b>：相同输入两次执行得到完全一致的结果（R12.8）。</li>
 * </ul>
 *
 * <p>数据经 {@link EngineIntegrationTestDataMapper} 直接种入引擎与 rule-config 共享的真实评级模型表
 * （{@code rating_model} / {@code rating_grade_band} / {@code rating_item}），随后<strong>从
 * MySQL 真实加载</strong>等级区间与评级子项并经真实 Aviator 求值器执行评分定级，执行前并
 * <strong>直接回读 MySQL 断言数据真实落库可重新读回</strong>（R15.3）。
 *
 * <p>真实 MySQL/Redis 不可用时由 {@link AbstractEngineMySqlRedisIntegrationTest} 的前置校验
 * <strong>失败而非跳过</strong>（R15.2/R15.3）。
 *
 * <p>幂等/自清理：本测试仅创建以 {@value #MARKER} 前缀命名的临时数据（与并行运行的直接定级集成测试
 * 任务 15.5 使用不同前缀避免冲突），并在每个用例前后按前缀清理（遵循逻辑外键依赖：先子表后主体表），
 * 绝不污染既有种子数据。
 */
class ScoreBasedGradingIntegrationTest extends AbstractEngineMySqlRedisIntegrationTest {

    /** 临时数据命名前缀：保证与种子数据及并行的直接定级测试（ZZIT_DG_）隔离，便于按前缀幂等清理。 */
    private static final String MARKER = "ZZIT_SBG_";

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
    void scoreBasedGrading_accrual_subItemCap_bandMapping_hitDetail_andDeterminism() {
        // —— 种子：SCORE_BASED 评级模型（商户·实时）——
        long modelId = insertRatingModel(MARKER + "MODEL_" + runId, "SCORE_BASED", "REALTIME", "MERCHANT");

        // 等级区间：[0,30)->三级, [30,60)->二级, [60,100]->一级（闭区间相邻，边界归低区间）
        insertGradeBand(modelId, bd(0), bd(30), "三级", 0);
        insertGradeBand(modelId, bd(30), bd(60), "二级", 1);
        insertGradeBand(modelId, bd(60), bd(100), "一级", 2);

        // 评级子项（Aviator 布尔条件 + 分值 + 子项分值上限）：
        //   item1: amount >= 100 命中，分值 50，上限 20  → 计入 min(50,20)=20（封顶，R12.2）
        //   item2: riskTags contains 'BLACKLIST' 命中，分值 25，无上限 → 计入 25
        //   item3: amount >= 100000 不命中（amount=500）→ 不计入
        // 命中总分 = 20 + 25 = 45 → 落入 [30,60) → 二级
        insertRatingItem(modelId, "信用", "大额交易", "amount >= 100", bd(50), bd(20), "HIGH");
        insertRatingItem(modelId, "名单", "命中黑名单", "string.contains(riskTags, 'BLACKLIST')", bd(25), null, "MID");
        insertRatingItem(modelId, "信用", "超大额交易", "amount >= 100000", bd(40), null, "HIGH");

        // —— R15.3：直接回读 MySQL 断言数据真实落库可重新读回 ——
        assertThat(countModelRows(modelId)).isEqualTo(1);
        assertThat(countBandRows(modelId)).isEqualTo(3);
        assertThat(countItemRows(modelId)).isEqualTo(3);

        // —— 从 MySQL 真实加载等级区间与评级子项 ——
        List<GradeBand> bands = loadGradeBands(modelId);
        List<RatingItem> items = loadRatingItems(modelId);
        assertThat(bands).hasSize(3);
        assertThat(items).hasSize(3);

        // —— 经真实 Aviator 求值器执行评分定级 ——
        ScoreBasedGrader grader = new ScoreBasedGrader(ruleExpressionEvaluator);
        Map<String, Object> context = Map.of("amount", 500, "riskTags", "VIP,BLACKLIST");
        ScoreBasedRatingResult result = grader.grade(items, bands, context);

        // 子项分值累加 + 封顶（R12.2/R12.3）：总分 = min(50,20) + 25 = 45
        assertThat(result.totalScore()).isEqualByComparingTo(bd(45));

        // 命中明细返回（R12.7）：命中两项，且封顶项计入值为 20
        assertThat(result.hitItems()).hasSize(2);
        ScoreBasedRatingResult.HitItem capped = result.hitItems().stream()
                .filter(h -> "大额交易".equals(h.subItem())).findFirst().orElseThrow();
        assertThat(capped.countedScore()).isEqualByComparingTo(bd(20));
        ScoreBasedRatingResult.HitItem listed = result.hitItems().stream()
                .filter(h -> "命中黑名单".equals(h.subItem())).findFirst().orElseThrow();
        assertThat(listed.countedScore()).isEqualByComparingTo(bd(25));

        // 总分落入区间得到等级（R12.4）：45 ∈ [30,60) → 二级
        assertThat(result.grade()).isEqualTo("二级");
        assertThat(result.outOfRange()).isFalse();
        assertThat(result.note()).isNull();

        // —— 定级结果可复现（R12.8）：相同输入二次执行得到完全一致结果 ——
        ScoreBasedRatingResult result2 = grader.grade(loadRatingItems(modelId), loadGradeBands(modelId), context);
        assertThat(result2.totalScore()).isEqualByComparingTo(result.totalScore());
        assertThat(result2.grade()).isEqualTo(result.grade());
        assertThat(result2.outOfRange()).isEqualTo(result.outOfRange());
        assertThat(result2.note()).isEqualTo(result.note());
        assertThat(result2.hitItems()).hasSize(result.hitItems().size());
    }

    // —————————————————— 种子辅助（直接写真实共享表） ——————————————————

    /** 插入评级模型（默认下线、版本 1），返回自增主键。 */
    private long insertRatingModel(String name, String gradingMode, String executionMode, String subject) {
        testData.insertRatingModel(name, MARKER + "EVT", executionMode, subject, gradingMode);
        Long id = testData.findRatingModelIdByName(name);
        assertThat(id).as("种子数据应真实落库并可读回主键: " + name).isNotNull();
        return id;
    }

    /** 插入等级区间。 */
    private void insertGradeBand(long modelId, BigDecimal minScore, BigDecimal maxScore, String grade, int orderNo) {
        testData.insertGradeBand(modelId, minScore, maxScore, grade, orderNo);
    }

    /** 插入评级子项（评分定级：condition_expr/score/sub_item_cap）。 */
    private void insertRatingItem(long modelId, String category, String subItem, String condition,
                                  BigDecimal score, BigDecimal subItemCap, String importance) {
        testData.insertScoreRatingItem(modelId, category, subItem, condition, score, subItemCap, importance);
    }

    // —————————————————— 加载辅助（从 MySQL 真实读回 R15.3） ——————————————————

    /** 从 MySQL 加载等级区间，按 order_no 升序。 */
    private List<GradeBand> loadGradeBands(long modelId) {
        return testData.findGradeBandsByModelId(modelId).stream()
                .map((EngineIntegrationTestRows.GradeBandRow row) ->
                        new GradeBand(row.getMinScore(), row.getMaxScore(), row.getGrade()))
                .toList();
    }

    /** 从 MySQL 加载评级子项，按 id 升序保证可复现的稳定顺序。 */
    private List<RatingItem> loadRatingItems(long modelId) {
        List<RatingItem> items = new ArrayList<>(testData.findScoreItemsByModelId(modelId).stream()
                .map((EngineIntegrationTestRows.ScoreItemRow row) -> new RatingItem(
                        row.getCategory(),
                        row.getSubItem(),
                        row.getConditionExpr(),
                        row.getScore(),
                        row.getSubItemCap(),
                        row.getImportance()))
                .toList());
        items.sort(Comparator.comparing(RatingItem::subItem, Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
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
