package com.riskplatform.engine.domain.decisionmatrix;

import com.riskplatform.engine.domain.asset.AssetRef;

import java.util.List;

/**
 * 决策矩阵定义（引擎执行侧，S9）。与 rule-config 配置同构（精简至执行所需）。
 *
 * <p><b>关联资产（扩展阶段 11.2，R9.3）</b>：{@code referencedAssets} 声明本矩阵执行前需先级联
 * 执行的下级资产（如矩阵的行/列维度变量取自一张评分卡的得分）。本执行器自身只读上下文字段值、
 * 不会自动级联，故由 {@code DecisionToolNodeHandler} 在执行本矩阵前按声明先执行这些被引用资产并把
 * 产出注入上下文（详见 DecisionToolNodeHandler）。旧调用与旧 JSON 缺省该字段时为空列表（无级联）。
 *
 * @param id               矩阵 id（命中决策来源标识）
 * @param rowVar           行维度变量
 * @param rowBins          行维度区间
 * @param colVar           列维度变量
 * @param colBins          列维度区间
 * @param cells            单元格
 * @param referencedAssets 被引用的下级关联资产（自动级联执行，R9.3，可空）
 */
public record DecisionMatrixDef(Long id, String rowVar, List<Bin> rowBins,
                                String colVar, List<Bin> colBins, List<Cell> cells,
                                List<AssetRef> referencedAssets) {

    /** 兼容旧签名构造器（无关联资产声明）：既有 6 参调用点（如 DecisionMatrixEvalController）无需改动。 */
    public DecisionMatrixDef(Long id, String rowVar, List<Bin> rowBins,
                             String colVar, List<Bin> colBins, List<Cell> cells) {
        this(id, rowVar, rowBins, colVar, colBins, cells, List.of());
    }

    public record Bin(double min, double max) {
    }

    public record Cell(int row, int col, String decision, int priority) {
    }
}
