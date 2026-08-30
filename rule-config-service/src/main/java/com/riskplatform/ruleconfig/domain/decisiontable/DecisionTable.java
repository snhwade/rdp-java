package com.riskplatform.ruleconfig.domain.decisiontable;

import com.riskplatform.common.error.ValidationException;

import java.util.List;

/**
 * 决策表聚合根（S2）。
 *
 * <p>一张决策表 = 输入变量列定义 + 若干条件行 + 命中策略。每行由若干列条件（变量+运算符+阈值）
 * 与一个输出（决策+优先级）组成。命中策略：
 * <ul>
 *   <li>FIRST：自上而下首个全部列条件满足的行命中；</li>
 *   <li>COLLECT：收集所有满足的行，全部参与决策聚合。</li>
 * </ul>
 *
 * <p>不变式：name/eventTypeCode 必填；columns/rows 非空；每行 conditions 非空、运算符合法。
 */
public class DecisionTable {

    public enum HitPolicy { FIRST, COLLECT }

    public enum Op { GT, GE, LT, LE, EQ, NE, BETWEEN, IN }

    private Long id;
    private String name;
    private String eventTypeCode;
    private HitPolicy hitPolicy;
    private List<Column> columns;
    private List<Row> rows;
    private String status;

    private DecisionTable() {
    }

    public static DecisionTable create(String name, String eventTypeCode, HitPolicy hitPolicy,
                                        List<Column> columns, List<Row> rows) {
        DecisionTable t = new DecisionTable();
        t.name = name;
        t.eventTypeCode = eventTypeCode;
        t.hitPolicy = hitPolicy == null ? HitPolicy.COLLECT : hitPolicy;
        t.columns = columns;
        t.rows = rows;
        t.status = "ENABLED";
        t.validate();
        return t;
    }

    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (name == null || name.isBlank()) {
            errors.field("name", "必填");
        }
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            errors.field("eventTypeCode", "必填");
        }
        if (columns == null || columns.isEmpty()) {
            errors.field("columns", "至少一列");
        }
        if (rows == null || rows.isEmpty()) {
            errors.field("rows", "至少一行");
        } else {
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                if (r.conditions() == null || r.conditions().isEmpty()) {
                    errors.field("rows[" + i + "].conditions", "至少一个条件");
                }
                if (r.decision() == null || r.decision().isBlank()) {
                    errors.field("rows[" + i + "].decision", "必填");
                }
            }
        }
        errors.throwIfAny();
    }

    public void update(String name, HitPolicy hitPolicy, List<Column> columns, List<Row> rows, String status) {
        this.name = name;
        this.hitPolicy = hitPolicy;
        this.columns = columns;
        this.rows = rows;
        this.status = status;
        validate();
    }

    /** 从持久化层重建（保留 status，避免 create 默认 ENABLED 覆盖）。 */
    public static DecisionTable rehydrate(Long id, String name, String eventTypeCode, HitPolicy hitPolicy,
                                          List<Column> columns, List<Row> rows, String status) {
        DecisionTable t = new DecisionTable();
        t.id = id;
        t.name = name;
        t.eventTypeCode = eventTypeCode;
        t.hitPolicy = hitPolicy == null ? HitPolicy.COLLECT : hitPolicy;
        t.columns = columns;
        t.rows = rows;
        t.status = status == null || status.isBlank() ? "ENABLED" : status;
        t.validate();
        return t;
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

    public HitPolicy getHitPolicy() {
        return hitPolicy;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public List<Row> getRows() {
        return rows;
    }

    public String getStatus() {
        return status;
    }

    /** 输入变量列：变量名 + 取值来源（context|indicator）。 */
    public record Column(String var, String source) {
    }

    /** 单元格条件：变量 + 运算符 + 阈值（BETWEEN 用 value/value2，IN 用 values）。 */
    public record Condition(String var, Op op, Double value, Double value2, List<String> values) {
    }

    /** 条件行：若干列条件 + 输出决策与优先级。 */
    public record Row(List<Condition> conditions, String decision, int priority) {
    }
}
