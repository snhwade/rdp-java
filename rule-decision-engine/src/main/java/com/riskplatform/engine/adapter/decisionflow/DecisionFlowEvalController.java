package com.riskplatform.engine.adapter.decisionflow;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.engine.application.DecisionFlowRuntimeLoader;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowEngine;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowEvaluator;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowResult;
import com.riskplatform.engine.infrastructure.runtime.RuntimeBindingReadMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.riskplatform.engine.domain.decisionmatrix.DecisionMatrixDef;
import com.riskplatform.engine.domain.decisiontable.DecisionTableDef;
import com.riskplatform.engine.domain.decisiontree.DecisionTreeDef;
import com.riskplatform.engine.domain.scorecard.ScorecardDef;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 决策流执行 REST 适配器（S4，扩展阶段 11.2 扩展节点级链路查询）。
 *
 * <p>两个端点：
 * <ul>
 *   <li>{@code POST /api/v1/decision-flows/evaluate}：沿用旧 {@link DecisionFlowEvaluator}（决策表/评分卡
 *       单路径），返回最终决策、执行路径与命中明细，保持既有兼容；</li>
 *   <li>{@code POST /api/v1/decision-flows/evaluate-trace}：用新 {@link DecisionFlowEngine}（9 类节点 +
 *       网关/分流/子流程 + 关联资产自动级联）执行，返回结果<strong>含节点级执行链路明细</strong>
 *       （节点 id/类型/输入快照/命中/赋值字段/级联资产产出），供执行链路查询展示（R9.4）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/decision-flows")
public class DecisionFlowEvalController {

    private final DecisionFlowEvaluator evaluator;
    private final DecisionFlowEngine engine;
    private final DecisionFlowRuntimeLoader runtimeLoader;
    private final RuntimeBindingReadMapper bindingMapper;

    public DecisionFlowEvalController(DecisionFlowEvaluator evaluator,
                                        DecisionFlowEngine engine,
                                        DecisionFlowRuntimeLoader runtimeLoader,
                                        RuntimeBindingReadMapper bindingMapper) {
        this.evaluator = evaluator;
        this.engine = engine;
        this.runtimeLoader = runtimeLoader;
        this.bindingMapper = bindingMapper;
    }

    /**
     * 决策流运行时调用（「决策流调用」）：按决策流 id 加载 ONLINE 版本并执行。
     */
    @PostMapping("/{id}/evaluate")
    public DecisionFlowRuntimeResponse evaluateById(@PathVariable("id") long flowId,
                                                    @RequestBody RuntimeEvalRequest request) {
        DecisionFlowRuntimeLoader.LoadedDecisionFlow loaded = runtimeLoader.load(flowId);
        if (loaded == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND,
                    "决策流不存在或已下线",
                    Map.of("decisionFlowId", String.valueOf(flowId)));
        }
        Map<String, Object> context = request.context() == null ? Map.of() : request.context();
        long started = System.currentTimeMillis();
        DecisionFlowResult result = engine.evaluate(loaded.definition(), context);
        return DecisionFlowRuntimeResponse.from(
                request.eventId(), loaded, result, System.currentTimeMillis() - started);
    }

    /** 按事件查询可绑定的规则包/决策流（供业务系统/网关解析）。 */
    @GetMapping("/bindings")
    public RuntimeBindingsResponse bindings(@org.springframework.web.bind.annotation.RequestParam String eventTypeCode) {
        return new RuntimeBindingsResponse(
                eventTypeCode,
                bindingMapper.selectRulePackageIdsByEvent(eventTypeCode),
                bindingMapper.selectEnabledFlowIdByEvent(eventTypeCode));
    }

    @PostMapping("/evaluate")
    public DecisionFlowResult evaluate(@RequestBody EvalRequest req) {
        DecisionFlowDef def = new DecisionFlowDef(
                req.nodes(),
                req.edges(),
                req.startNodeId(),
                req.decisionTables() == null ? Map.of() : req.decisionTables(),
                req.scorecards() == null ? Map.of() : req.scorecards());
        return evaluator.evaluate(def, req.context() == null ? Map.of() : req.context());
    }

    /**
     * 决策流执行 + 节点级执行链路查询（R9.4）。
     *
     * <p>用新引擎执行，支持内联决策表/评分卡/决策树/决策矩阵定义（及其声明的关联资产，R9.3），
     * 返回的 {@link DecisionFlowResult#trace()} 携带逐节点输入/输出/命中/赋值字段/级联产出明细，
     * 可被前端执行链路视图直接消费展示。
     */
    @PostMapping("/evaluate-trace")
    public DecisionFlowResult evaluateTrace(@RequestBody TraceEvalRequest req) {
        DecisionFlowDef def = new DecisionFlowDef(
                req.nodes(),
                req.edges(),
                req.startNodeId(),
                req.decisionTables() == null ? Map.of() : req.decisionTables(),
                req.scorecards() == null ? Map.of() : req.scorecards(),
                req.decisionTrees() == null ? Map.of() : req.decisionTrees(),
                req.decisionMatrices() == null ? Map.of() : req.decisionMatrices());
        return engine.evaluate(def, req.context() == null ? Map.of() : req.context());
    }

    public record EvalRequest(
            List<DecisionFlowDef.Node> nodes,
            List<DecisionFlowDef.Edge> edges,
            String startNodeId,
            Map<Long, DecisionTableDef> decisionTables,
            Map<Long, ScorecardDef> scorecards,
            Map<String, Object> context) {
    }

    /** 节点级链路查询请求：在 {@link EvalRequest} 基础上支持内联决策树/决策矩阵定义。 */
    public record TraceEvalRequest(
            List<DecisionFlowDef.Node> nodes,
            List<DecisionFlowDef.Edge> edges,
            String startNodeId,
            Map<Long, DecisionTableDef> decisionTables,
            Map<Long, ScorecardDef> scorecards,
            Map<Long, DecisionTreeDef> decisionTrees,
            Map<Long, DecisionMatrixDef> decisionMatrices,
            Map<String, Object> context) {
    }

    public record RuntimeEvalRequest(String eventId, Map<String, Object> context) {
    }

    public record RuntimeBindingsResponse(String eventTypeCode,
                                          List<Long> rulePackageIds,
                                          Long decisionFlowId) {
    }

    public record DecisionFlowRuntimeResponse(
            String eventId,
            long decisionFlowId,
            Integer flowVersion,
            String definitionSource,
            String decision,
            List<String> path,
            List<HitView> hits,
            long elapsedMs) {

        static DecisionFlowRuntimeResponse from(String eventId,
                                                DecisionFlowRuntimeLoader.LoadedDecisionFlow loaded,
                                                DecisionFlowResult result,
                                                long elapsedMs) {
            List<HitView> hits = result.hits().stream()
                    .map(h -> new HitView(h.ruleId(), h.decision().name(), h.trialRun()))
                    .toList();
            return new DecisionFlowRuntimeResponse(
                    eventId,
                    loaded.flowId(),
                    loaded.version(),
                    loaded.source(),
                    result.finalDecision(),
                    result.path(),
                    hits,
                    elapsedMs);
        }
    }

    public record HitView(long ruleId, String decision, boolean trialRun) {
    }
}
