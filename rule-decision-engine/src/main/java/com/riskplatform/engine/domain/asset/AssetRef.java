package com.riskplatform.engine.domain.asset;

/**
 * 决策资产对「下级关联资产」的引用声明（扩展阶段 11.2，R9.3）。
 *
 * <p>背景：执行侧的决策工具定义（{@code DecisionTableDef}/{@code ScorecardDef}/
 * {@code DecisionTreeDef}/{@code DecisionMatrixDef}）原本是「自洽」的——它们只读取
 * 执行上下文 {@code Map<String,Object>} 中的字段值，并不知道某个输入字段其实应由<strong>另一个
 * 决策资产</strong>先算出来（如决策矩阵的行维度变量取自一张评分卡的得分）。因此各 Evaluator
 * <strong>不会</strong>自动级联执行被引用的下级资产（详见各 Evaluator 注释与本任务实现说明）。
 *
 * <p>本记录用于在决策工具定义上「声明」其依赖的下级关联资产，使决策流节点处理器
 * （{@code DecisionToolNodeHandler}）在执行主资产前，能<strong>自动级联</strong>先执行这些被引用资产，
 * 并把它们的产出以 {@link #outputKey()} 登记进执行上下文，供主资产作为输入变量取用——无需在
 * 决策流中显式为下级资产单独配置节点（R9.3）。
 *
 * @param type      被引用资产类型
 * @param refId     被引用资产 id（在决策流内联定义映射中按 id 查得其定义）
 * @param outputKey 被引用资产产出登记到执行上下文的键名（主资产的变量按此键取用）；
 *                  为空时按 {@code type 小写_refId} 生成默认键（如 {@code scorecard_5}）
 */
public record AssetRef(AssetType type, Long refId, String outputKey) {

    /** 关联资产类型（与决策工具四类对应）。 */
    public enum AssetType {
        DECISION_TABLE,
        SCORECARD,
        DECISION_TREE,
        DECISION_MATRIX
    }

    /** 解析产出登记键：显式 outputKey 优先，否则按「类型小写_refId」生成默认键。 */
    public String resolveOutputKey() {
        if (outputKey != null && !outputKey.isBlank()) {
            return outputKey.trim();
        }
        String typeName = type == null ? "asset" : type.name().toLowerCase();
        return typeName + "_" + refId;
    }
}
