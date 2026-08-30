package com.riskplatform.engine.domain.decisionflow.node;

import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;

/**
 * 网关节点处理器（条件网关 / 并行网关，扩展阶段 10.1）。
 *
 * <p>网关是<b>纯路径节点</b>：本身不执行决策资产、不产命中决策、不产赋值字段，故 {@link #handle}
 * 始终返回 {@link NodeResult#empty()}。真正的「选路」逻辑由
 * {@link com.riskplatform.engine.domain.decisionflow.DecisionFlowEngine} 的遍历器实现：
 * <ul>
 *   <li>条件网关（{@code CONDITION_GATEWAY}）：按出线顺序取第一条满足条件的边，无满足走默认边（R7.1/7.2）；</li>
 *   <li>并行网关（{@code PARALLEL_GATEWAY}）：分叉网关并行执行各路径、汇聚网关合并后继续（R7.3）。</li>
 * </ul>
 *
 * <p>之所以仍注册显式处理器（而非依赖注册表的未注册兜底），是为了让网关类型「被识别且语义明确」，
 * 并在 {@link NodeHandlerRegistry#supports} 中返回 true，便于后续扩展与排查。
 * 一个实例只对应一种类型，故按构造入参区分注册条件网关与并行网关两个实例。
 */
public final class GatewayNodeHandler implements NodeHandler {

    private final DecisionFlowDef.NodeType type;

    public GatewayNodeHandler(DecisionFlowDef.NodeType type) {
        if (type != DecisionFlowDef.NodeType.CONDITION_GATEWAY
                && type != DecisionFlowDef.NodeType.PARALLEL_GATEWAY) {
            throw new IllegalArgumentException(
                    "GatewayNodeHandler 仅支持 CONDITION_GATEWAY/PARALLEL_GATEWAY，传入=" + type);
        }
        this.type = type;
    }

    @Override
    public DecisionFlowDef.NodeType supportedType() {
        return type;
    }

    @Override
    public NodeResult handle(DecisionFlowDef.Node node, FlowContext ctx) {
        // 网关仅作路径节点，不产决策；选路在引擎遍历器中完成
        return NodeResult.empty();
    }
}
