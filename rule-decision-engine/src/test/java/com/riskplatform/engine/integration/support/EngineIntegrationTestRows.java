package com.riskplatform.engine.integration.support;

import java.math.BigDecimal;

/** 引擎集成测试 MyBatis 查询行映射。 */
public final class EngineIntegrationTestRows {

    private EngineIntegrationTestRows() {
    }

    public static class GradeBandRow {
        private BigDecimal minScore;
        private BigDecimal maxScore;
        private String grade;

        public BigDecimal getMinScore() { return minScore; }
        public void setMinScore(BigDecimal minScore) { this.minScore = minScore; }
        public BigDecimal getMaxScore() { return maxScore; }
        public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }
    }

    public static class DirectItemRow {
        private String conditionExpr;
        private String grade;

        public String getConditionExpr() { return conditionExpr; }
        public void setConditionExpr(String conditionExpr) { this.conditionExpr = conditionExpr; }
        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }
    }

    public static class ScoreItemRow {
        private String category;
        private String subItem;
        private String conditionExpr;
        private BigDecimal score;
        private BigDecimal subItemCap;
        private String importance;

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getSubItem() { return subItem; }
        public void setSubItem(String subItem) { this.subItem = subItem; }
        public String getConditionExpr() { return conditionExpr; }
        public void setConditionExpr(String conditionExpr) { this.conditionExpr = conditionExpr; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
        public BigDecimal getSubItemCap() { return subItemCap; }
        public void setSubItemCap(BigDecimal subItemCap) { this.subItemCap = subItemCap; }
        public String getImportance() { return importance; }
        public void setImportance(String importance) { this.importance = importance; }
    }
}
