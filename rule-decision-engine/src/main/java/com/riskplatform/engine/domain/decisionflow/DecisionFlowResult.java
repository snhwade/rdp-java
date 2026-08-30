package com.riskplatform.engine.domain.decisionflow;

import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.List;

/**
 * 决策流执行结果（S4，扩展阶段 11.2 扩展执行链路）。
 *
 * <p>原仅携带 {@code path}（节点 id 顺序）与 {@code hits}（命中汇总）。本任务（R9.4）新增
 * {@code trace}：逐节点的执行链路明细（节点 id/类型/输入快照/命中/赋值字段/级联资产产出），
 * 供执行链路查询展示节点级过程。{@code path} 与 {@code hits} 保留不变，既有消费方与端点兼容。
 *
 * @param finalDecision 最终决策（聚合所有命中后得出）
 * @param path          执行经过的节点 id 顺序
 * @param hits          各节点产出的命中决策
 * @param trace         逐节点执行链路明细（R9.4，与 path 同序）
 */
public record DecisionFlowResult(String finalDecision, List<String> path, List<HitDecision> hits,
                                 List<FlowTraceStep> trace) {

    public DecisionFlowResult {
        path = path == null ? List.of() : List.copyOf(path);
        hits = hits == null ? List.of() : List.copyOf(hits);
        trace = trace == null ? List.of() : List.copyOf(trace);
    }

    /** 兼容旧三参构造（无节点级链路明细）：既有调用方（如旧 DecisionFlowEvaluator）无需改动。 */
    public DecisionFlowResult(String finalDecision, List<String> path, List<HitDecision> hits) {
        this(finalDecision, path, hits, List.of());
    }
}
