package com.riskplatform.ruleconfig.domain.decisionmatrix;

import com.riskplatform.common.error.ValidationException;

import java.util.List;

/**
 * 决策矩阵聚合根（S9）。
 *
 * <p>二维交叉决策：行维度区间 × 列维度区间 → 单元格决策。
 *
 * <p>不变式：name/eventTypeCode/rowVar/colVar 必填；rowBins/colBins/cells 非空。
 */
public class DecisionMatrix {

    private Long id;
    private String name;
    private String eventTypeCode;
    private String rowVar;
    private List<Bin> rowBins;
    private String colVar;
    private List<Bin> colBins;
    private List<Cell> cells;
    private String status;

    private DecisionMatrix() {
    }

    public static DecisionMatrix create(String name, String eventTypeCode, String rowVar, List<Bin> rowBins,
                                        String colVar, List<Bin> colBins, List<Cell> cells) {
        DecisionMatrix m = new DecisionMatrix();
        m.name = name;
        m.eventTypeCode = eventTypeCode;
        m.rowVar = rowVar;
        m.rowBins = rowBins;
        m.colVar = colVar;
        m.colBins = colBins;
        m.cells = cells;
        m.status = "ENABLED";
        m.validate();
        return m;
    }

    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (name == null || name.isBlank()) {
            errors.field("name", "必填");
        }
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            errors.field("eventTypeCode", "必填");
        }
        if (rowVar == null || rowVar.isBlank()) {
            errors.field("rowVar", "必填");
        }
        if (colVar == null || colVar.isBlank()) {
            errors.field("colVar", "必填");
        }
        if (rowBins == null || rowBins.isEmpty()) {
            errors.field("rowBins", "至少一个行区间");
        }
        if (colBins == null || colBins.isEmpty()) {
            errors.field("colBins", "至少一个列区间");
        }
        if (cells == null || cells.isEmpty()) {
            errors.field("cells", "至少一个单元格");
        }
        errors.throwIfAny();
    }

    public void update(String name, String rowVar, List<Bin> rowBins, String colVar,
                       List<Bin> colBins, List<Cell> cells, String status) {
        this.name = name;
        this.rowVar = rowVar;
        this.rowBins = rowBins;
        this.colVar = colVar;
        this.colBins = colBins;
        this.cells = cells;
        this.status = status;
        validate();
    }

    /** 从持久化层重建（保留 status）。 */
    public static DecisionMatrix rehydrate(Long id, String name, String eventTypeCode, String rowVar,
                                           List<Bin> rowBins, String colVar, List<Bin> colBins,
                                           List<Cell> cells, String status) {
        DecisionMatrix m = new DecisionMatrix();
        m.id = id;
        m.name = name;
        m.eventTypeCode = eventTypeCode;
        m.rowVar = rowVar;
        m.rowBins = rowBins;
        m.colVar = colVar;
        m.colBins = colBins;
        m.cells = cells;
        m.status = status == null || status.isBlank() ? "ENABLED" : status;
        m.validate();
        return m;
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

    public String getRowVar() {
        return rowVar;
    }

    public List<Bin> getRowBins() {
        return rowBins;
    }

    public String getColVar() {
        return colVar;
    }

    public List<Bin> getColBins() {
        return colBins;
    }

    public List<Cell> getCells() {
        return cells;
    }

    public String getStatus() {
        return status;
    }

    /** 数值区间 [min, max)。 */
    public record Bin(double min, double max) {
    }

    /** 单元格：行索引 × 列索引 → 决策 + 优先级。 */
    public record Cell(int row, int col, String decision, int priority) {
    }
}
