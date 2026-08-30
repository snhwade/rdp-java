package com.riskplatform.engine.domain.decisiontree;

import com.riskplatform.engine.domain.asset.AssetRef;

import java.util.List;

/**
 * 决策树定义（引擎执行侧，S8）。与 rule-config 配置同构（精简至执行所需）。
 *
 * <p><b>关联资产（扩展阶段 11.2，R9.3）</b>：{@code referencedAssets} 声明本决策树执行前需先
 * 级联执行的下级资产（如分支条件引用另一资产的产出）。本执行器自身只读上下文字段值、不会自动级联，
 * 故由 {@code DecisionToolNodeHandler} 在执行本树前按声明先执行被引用资产并把产出注入上下文。
 * 旧调用与旧 JSON 缺省该字段时为空列表（无级联）。
 *
 * @param id               决策树 id（命中决策来源标识）
 * @param rootNodeId       根节点 id
 * @param nodes            节点列表
 * @param referencedAssets 被引用的下级关联资产（自动级联执行，R9.3，可空）
 */
public record DecisionTreeDef(Long id, String rootNodeId, List<Node> nodes,
                              List<AssetRef> referencedAssets) {

    /** 兼容旧签名构造器（无关联资产声明）：既有 3 参调用点（如 DecisionTreeEvalController）无需改动。 */
    public DecisionTreeDef(Long id, String rootNodeId, List<Node> nodes) {
        this(id, rootNodeId, nodes, List.of());
    }

    /** 节点：内部节点有 children（条件→子节点）；叶子节点 leaf=true 带决策。 */
    public record Node(String nodeId, boolean leaf, String decision, Integer priority, List<Branch> children) {
    }

    /** 分支：满足 condition(Aviator) 则进入 childNodeId。 */
    public record Branch(String condition, String childNodeId) {
    }
}
