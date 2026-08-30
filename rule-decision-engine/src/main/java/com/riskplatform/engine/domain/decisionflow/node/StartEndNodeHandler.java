package com.riskplatform.engine.domain.decisionflow.node;

import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;

/**
 * 起止节点处理器（START/END，扩展阶段）。
 *
 * <p>起止节点不产决策、不产赋值字段：START 仅作为流程入口，END 作为终点（聚合在引擎主循环完成）。
 * 一个实例只能对应一种类型，故按构造入参区分注册两个实例（START 与 END）。
 */
public final class StartEndNodeHandler implements NodeHandler {

    private final DecisionFlowDef.NodeType type;

    public StartEndNodeHandler(DecisionFlowDef.NodeType type) {
        if (type != DecisionFlowDef.NodeType.START && type != DecisionFlowDef.NodeType.END) {
            throw new IllegalArgumentException("StartEndNodeHandler 仅支持 START/END，传入=" + type);
        }
        this.type = type;
    }

    @Override
    public DecisionFlowDef.NodeType supportedType() {
        return type;
    }

    @Override
    public NodeResult handle(DecisionFlowDef.Node node, FlowContext ctx) {
        // 起止节点不产决策
        return NodeResult.empty();
    }
}
