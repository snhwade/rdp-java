package com.riskplatform.engine.domain.decisiontable;

import com.riskplatform.engine.domain.asset.AssetRef;

import java.util.List;

/**
 * 决策表定义（引擎执行侧，S2）。
 *
 * <p>与 rule-config 的决策表配置同构（精简至执行所需）：列定义 + 条件行 + 命中策略。
 * 由引擎从配置服务同步而来或随请求传入。
 *
 * <p><b>关联资产（扩展阶段 11.2，R9.3）</b>：{@code referencedAssets} 声明本决策表执行前需先
 * 级联执行的下级资产（如某列条件变量取自另一资产的产出）。本执行器自身只读上下文字段值、不会自动
 * 级联，故由 {@code DecisionToolNodeHandler} 在执行本表前按声明先执行被引用资产并把产出注入上下文。
 * 旧调用与旧 JSON 缺省该字段时为空列表（无级联）。
 *
 * @param id               决策表 id（命中决策的来源标识）
 * @param name             名称
 * @param hitPolicy        命中策略 FIRST/COLLECT
 * @param rows             条件行
 * @param referencedAssets 被引用的下级关联资产（自动级联执行，R9.3，可空）
 */
public record DecisionTableDef(Long id, String name, HitPolicy hitPolicy, List<Row> rows,
                               List<AssetRef> referencedAssets) {

    /** 兼容旧签名构造器（无关联资产声明）：既有 4 参调用点（如 DecisionTableEvalController）无需改动。 */
    public DecisionTableDef(Long id, String name, HitPolicy hitPolicy, List<Row> rows) {
        this(id, name, hitPolicy, rows, List.of());
    }

    public enum HitPolicy { FIRST, COLLECT }

    public enum Op { GT, GE, LT, LE, EQ, NE, BETWEEN, IN }

    /** 单元格条件：变量 + 运算符 + 阈值。 */
    public record Condition(String var, Op op, Double value, Double value2, List<String> values) {
    }

    /** 条件行：列条件全部满足则该行命中 → 输出决策与优先级。 */
    public record Row(List<Condition> conditions, String decision, int priority) {
    }
}
