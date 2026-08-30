package com.riskplatform.engine.domain.decisionflow.node;

import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 节点处理器注册表/分发器（扩展阶段）。
 *
 * <p>按 {@link DecisionFlowDef.NodeType} 注册并分派 {@link NodeHandler}。后续 9.3/10.x 新增
 * 处理器时只需注册新实现，无需改动引擎主循环——「扩展而非破坏」。
 *
 * <p>未注册类型的兜底：返回空结果（不产决策、不中断），保证含未实现节点类型的决策流仍可走完，
 * 兼容历史与增量演进。
 */
public final class NodeHandlerRegistry {

    private final Map<DecisionFlowDef.NodeType, NodeHandler> handlers =
            new EnumMap<>(DecisionFlowDef.NodeType.class);

    public NodeHandlerRegistry(List<NodeHandler> handlerList) {
        if (handlerList != null) {
            for (NodeHandler h : handlerList) {
                register(h);
            }
        }
    }

    public void register(NodeHandler handler) {
        handlers.put(handler.supportedType(), handler);
    }

    /** 是否存在该类型的处理器。 */
    public boolean supports(DecisionFlowDef.NodeType type) {
        return handlers.containsKey(type);
    }

    /**
     * 分派执行：找到对应处理器执行；未注册则返回空结果（兜底，不中断流程）。
     */
    public NodeResult dispatch(DecisionFlowDef.Node node, FlowContext ctx) {
        NodeHandler handler = handlers.get(node.type());
        if (handler == null) {
            return NodeResult.empty();
        }
        return handler.handle(node, ctx);
    }
}
