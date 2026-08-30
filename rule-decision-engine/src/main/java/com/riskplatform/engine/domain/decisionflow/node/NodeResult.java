package com.riskplatform.engine.domain.decisionflow.node;

import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.List;
import java.util.Map;

/**
 * 节点执行结果（扩展阶段）。
 *
 * <p>由各 {@link NodeHandler} 产出，{@link com.riskplatform.engine.domain.decisionflow.DecisionFlowEngine}
 * 据此把命中决策并入累计结果、把赋值字段登记进 {@link FlowContext}，并据 {@link #cascaded()} 构建
 * 节点级执行链路明细（11.2，R9.4）。
 *
 * @param hits        本节点产出的命中决策（可空/空，START/END/网关一般为空）
 * @param assignments 本节点产出的赋值字段（key→value，注入后续节点上下文，可空）
 * @param cascaded    本节点执行主资产前自动级联执行的关联资产产出（11.2，R9.3；键=登记键，值=产出值，可空）。
 *                    仅决策工具节点在存在关联资产声明时非空，供链路记录展示级联现状。
 */
public record NodeResult(List<HitDecision> hits, Map<String, Object> assignments, Map<String, Object> cascaded) {

    public NodeResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
        assignments = assignments == null ? Map.of() : Map.copyOf(assignments);
        cascaded = cascaded == null ? Map.of() : Map.copyOf(cascaded);
    }

    /** 兼容旧两参构造（无级联产出）：既有处理器无需改动。 */
    public NodeResult(List<HitDecision> hits, Map<String, Object> assignments) {
        this(hits, assignments, Map.of());
    }

    /** 空结果（START/END 等不产出决策的节点）。 */
    public static NodeResult empty() {
        return new NodeResult(List.of(), Map.of(), Map.of());
    }

    public static NodeResult ofHits(List<HitDecision> hits) {
        return new NodeResult(hits, Map.of(), Map.of());
    }
}
