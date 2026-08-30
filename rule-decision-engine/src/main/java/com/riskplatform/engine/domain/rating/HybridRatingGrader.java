package com.riskplatform.engine.domain.rating;

import com.riskplatform.engine.domain.rule.RuleExpressionEvaluator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 混合定级器：同一评级模型内同时包含评分子项与直接定级项（gradingMode=MIXED）。
 *
 * <p>执行规则：
 * <ol>
 *   <li>评分子项累加为 scoreTotal，并映射为 scoreGrade；</li>
 *   <li>直接定级项命中后取最高等级 directGrade，命中项得分各取对应等级区间下界；</li>
 *   <li>展示总分 = scoreTotal + 直接命中项得分之和；</li>
 *   <li>最终等级 = scoreGrade 与 directGrade 中等级序较高者。</li>
 * </ol>
 */
public class HybridRatingGrader {

    private final ScoreBasedGrader scoreGrader;
    private final DirectGrader directGrader;

    public HybridRatingGrader(RuleExpressionEvaluator conditionEvaluator) {
        this.scoreGrader = new ScoreBasedGrader(conditionEvaluator);
        this.directGrader = new DirectGrader(conditionEvaluator);
    }

    HybridRatingGrader(ScoreBasedGrader scoreGrader, DirectGrader directGrader) {
        this.scoreGrader = scoreGrader;
        this.directGrader = directGrader;
    }

    public HybridRatingResult grade(List<RatingItem> scoreItems,
                                    List<DirectGradingItem> directItems,
                                    List<GradeBand> bands,
                                    Map<String, Object> context) {
        List<RatingItem> safeScoreItems = scoreItems == null ? List.of() : scoreItems;
        List<DirectGradingItem> safeDirectItems = directItems == null ? List.of() : directItems;
        List<GradeBand> safeBands = bands == null ? List.of() : bands;
        Map<String, Object> env = context == null ? Map.of() : context;

        ScoreBasedRatingResult scoreResult = scoreGrader.grade(safeScoreItems, safeBands, env);
        GradeOrder order = GradeOrder.fromBands(safeBands);
        DirectGradingResult directResult = directGrader.grade(safeDirectItems, order, env);

        BigDecimal directScore = sumDirectMinScores(directResult.hitItems(), safeBands);
        BigDecimal totalScore = scoreResult.totalScore().add(directScore);

        String finalGrade = resolveFinalGrade(scoreResult.grade(), directResult, order);
        return new HybridRatingResult(totalScore, finalGrade, scoreResult, directResult, directScore);
    }

    private BigDecimal sumDirectMinScores(List<DirectGradingItem> hitItems, List<GradeBand> bands) {
        BigDecimal sum = BigDecimal.ZERO;
        for (DirectGradingItem item : hitItems) {
            BigDecimal min = minScoreForGrade(bands, item.grade());
            if (min != null) {
                sum = sum.add(min);
            }
        }
        return sum;
    }

    private BigDecimal minScoreForGrade(List<GradeBand> bands, String grade) {
        for (GradeBand band : bands) {
            if (grade != null && grade.equals(band.grade())) {
                return band.minScore();
            }
        }
        return null;
    }

    private String resolveFinalGrade(String scoreGrade, DirectGradingResult directResult, GradeOrder order) {
        if (!directResult.graded()) {
            return scoreGrade;
        }
        if (scoreGrade == null || scoreGrade.isBlank()) {
            return directResult.grade();
        }
        return order.highest(List.of(scoreGrade, directResult.grade()));
    }

    public record HybridRatingResult(BigDecimal totalScore,
                                     String grade,
                                     ScoreBasedRatingResult scoreResult,
                                     DirectGradingResult directResult,
                                     BigDecimal directScoreContribution) {
    }
}
