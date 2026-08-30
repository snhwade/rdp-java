package com.riskplatform.ruleconfig.domain.scorecard;

import com.riskplatform.common.error.ValidationException;

import java.util.List;

/**
 * 评分卡聚合根（S3）。
 *
 * <p>一张评分卡 = 若干变量（每变量含条件区间→分值、权重）+ 等级区间（总分→等级+决策）。
 * 总分 = Σ(命中区间分值 × 变量权重)，按等级区间映射决策。
 *
 * <p>不变式：name/eventTypeCode 必填；variables/levels 非空；每变量 bins 非空。
 */
public class Scorecard {

    public enum Op { GT, GE, LT, LE, EQ, NE, BETWEEN, IN }

    private Long id;
    private String name;
    private String eventTypeCode;
    private List<Variable> variables;
    private List<Level> levels;
    private String status;

    private Scorecard() {
    }

    public static Scorecard create(String name, String eventTypeCode,
                                    List<Variable> variables, List<Level> levels) {
        Scorecard s = new Scorecard();
        s.name = name;
        s.eventTypeCode = eventTypeCode;
        s.variables = variables;
        s.levels = levels;
        s.status = "ENABLED";
        s.validate();
        return s;
    }

    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (name == null || name.isBlank()) {
            errors.field("name", "必填");
        }
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            errors.field("eventTypeCode", "必填");
        }
        if (variables == null || variables.isEmpty()) {
            errors.field("variables", "至少一个变量");
        } else {
            for (int i = 0; i < variables.size(); i++) {
                if (variables.get(i).bins() == null || variables.get(i).bins().isEmpty()) {
                    errors.field("variables[" + i + "].bins", "至少一个条件区间");
                }
            }
        }
        if (levels == null || levels.isEmpty()) {
            errors.field("levels", "至少一个等级区间");
        }
        errors.throwIfAny();
    }

    public void update(String name, List<Variable> variables, List<Level> levels, String status) {
        this.name = name;
        this.variables = variables;
        this.levels = levels;
        this.status = status;
        validate();
    }

    /** 从持久化层重建（保留 status）。 */
    public static Scorecard rehydrate(Long id, String name, String eventTypeCode,
                                    List<Variable> variables, List<Level> levels, String status) {
        Scorecard s = new Scorecard();
        s.id = id;
        s.name = name;
        s.eventTypeCode = eventTypeCode;
        s.variables = variables;
        s.levels = levels;
        s.status = status == null || status.isBlank() ? "ENABLED" : status;
        s.validate();
        return s;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public List<Variable> getVariables() {
        return variables;
    }

    public List<Level> getLevels() {
        return levels;
    }

    public String getStatus() {
        return status;
    }

    /** 评分变量：变量名 + 取值来源 + 权重 + 缺省分 + 条件区间。 */
    public record Variable(String var, String source, double weight, double defaultScore, List<Bin> bins) {
    }

    /** 条件区间：运算符 + 阈值 → 命中得分。 */
    public record Bin(Op op, Double value, Double value2, List<String> values, double score) {
    }

    /** 等级区间：[minScore, maxScore) → 等级 + 决策 + 优先级。 */
    public record Level(double minScore, double maxScore, String level, String decision, int priority) {
    }
}
