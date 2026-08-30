package com.riskplatform.engine.domain.decisionflow;

import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.List;
import java.util.Map;

/**
 * 决策流节点级执行链路明细（扩展阶段 11.2，R9.4）。
 *
 * <p>{@code DecisionFlowResult} 原本只携带 {@code path}（经过的节点 id 顺序）与 {@code hits}
 * （命中决策汇总），无法回答「某节点执行时看到的输入是什么、产出了哪些命中与赋值字段、是否自动
 * 级联执行了关联资产」。本记录补齐节点级链路明细，逐节点记录：节点身份、输入快照、命中、赋值字段、
 * 级联资产产出，供执行链路查询展示（R9.4）。
 *
 * <p>{@code inputSnapshot} 为节点执行<strong>前</strong>上下文 env 的浅拷贝快照（仅保留可序列化的
 * 标量/字符串等，避免把大对象带入链路）；{@code assignments} 为本节点产出登记的赋值字段；
 * {@code cascaded} 记录本节点执行主资产前自动级联执行的关联资产产出（R9.3，键=登记键，值=产出值），
 * 为空表示无级联。
 *
 * @param nodeId        节点 id
 * @param nodeType      节点类型名（如 DECISION_TOOL/RULE_PACKAGE/MODEL/CONDITION_GATEWAY...）
 * @param refType       引用资产类型（可空）
 * @param refId         引用资产 id（可空）
 * @param inputSnapshot 节点执行前的输入快照（含赋值字段，R9.4）
 * @param hits          本节点产出的命中决策
 * @param assignments   本节点产出的赋值字段（注入后续节点，R9.1）
 * @param cascaded      本节点自动级联执行的关联资产产出（R9.3；键→产出值，可空）
 */
public record FlowTraceStep(
        String nodeId,
        String nodeType,
        String refType,
        Long refId,
        Map<String, Object> inputSnapshot,
        List<HitDecision> hits,
        Map<String, Object> assignments,
        Map<String, Object> cascaded) {

    public FlowTraceStep {
        inputSnapshot = inputSnapshot == null ? Map.of() : Map.copyOf(inputSnapshot);
        hits = hits == null ? List.of() : List.copyOf(hits);
        assignments = assignments == null ? Map.of() : Map.copyOf(assignments);
        cascaded = cascaded == null ? Map.of() : Map.copyOf(cascaded);
    }
}
