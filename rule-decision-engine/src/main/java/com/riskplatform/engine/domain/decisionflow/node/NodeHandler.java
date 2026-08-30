package com.riskplatform.engine.domain.decisionflow.node;

import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;

/**
 * 决策流节点处理器（扩展阶段，R6/R7/R8）。
 *
 * <p>每种 {@link DecisionFlowDef.NodeType} 由一个实现负责执行该节点逻辑：读取/写入
 * {@link FlowContext} 求值环境，产出命中决策与赋值字段（{@link NodeResult}）。
 * 由 {@link NodeHandlerRegistry} 按节点类型分派。
 *
 * <p>本任务（9.2）仅落地：接口 + 分派骨架 + START/END/LIST_CHECK/决策工具处理器；
 * 规则包/模型/网关/分流/子流程处理器在 9.3/10.x 实现。
 */
public interface NodeHandler {

    /** 本处理器负责的节点类型。 */
    DecisionFlowDef.NodeType supportedType();

    /**
     * 执行节点逻辑。
     *
     * @param node 当前节点
     * @param ctx  决策流执行上下文（可读写求值环境）
     * @return 节点执行结果（命中决策 + 赋值字段）
     */
    NodeResult handle(DecisionFlowDef.Node node, FlowContext ctx);
}
