package com.riskplatform.engine.domain.decisionflow;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.decision.DecisionAggregator;
import com.riskplatform.engine.domain.decisionflow.node.FlowContext;
import com.riskplatform.engine.domain.decisionflow.node.NodeHandlerRegistry;
import com.riskplatform.engine.domain.decisionflow.node.NodeResult;
import com.riskplatform.engine.domain.decisionflow.node.SubFlowGuard;
import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 决策流引擎（扩展阶段，演进自 {@link DecisionFlowEvaluator}）。
 *
 * <p>与旧 {@code DecisionFlowEvaluator} 的差异：节点执行不再用内置 switch，而是委派给
 * {@link NodeHandlerRegistry} 按 {@link DecisionFlowDef.NodeType} 分派到各 {@code NodeHandler}。
 * 这样新增节点类型（规则包/模型/网关/分流/子流程，9.3/10.x）只需注册新处理器，不改主循环。
 *
 * <p><b>遍历模型（10.1 重构）</b>：从单路径 while 循环改造为支持「分叉-并行-汇聚」的分治遍历，
 * 以支持并行网关（R7.3）。遍历规则：
 * <ul>
 *   <li><b>串行/普通节点</b>：执行后按「满足条件的首条出边、无条件边兜底」流向下游（R7.4，与旧逻辑一致）。</li>
 *   <li><b>条件网关 CONDITION_GATEWAY</b>（R7.1/7.2）：节点本身不产决策；按出线顺序取第一条满足条件的边，
 *       仅走一条；都不满足走默认边（{@code isDefault=true}）；无默认边则记录无匹配并终止该路径（降级）。</li>
 *   <li><b>并行网关 PARALLEL_GATEWAY</b>（R7.3）：识别分叉网关（出度&gt;1）→ 对每条出边递归执行子路径，
 *       子路径在抵达配对的汇聚网关（入度&gt;1）处停止 → 合并各子路径命中与赋值字段（共享同一
 *       {@link FlowContext} 自然累积）→ 从汇聚网关沿其唯一出边继续。</li>
 * </ul>
 *
 * <p><b>网关配对识别</b>：不依赖节点显式配对字段，而是按图的度数推断——并行网关中「出度&gt;1」者视为
 * 分叉网关，「入度&gt;1」者视为汇聚网关。分叉处理器递归各分支，分支遇到任一汇聚网关即停并上报其 id，
 * 各分支上报的汇聚 id 收敛为同一节点（保存期由 config 侧 {@code validateStructure} 校验成对）。
 *
 * <p><b>兼容策略</b>：本类与旧 {@code DecisionFlowEvaluator} 并存（旧执行路径与既有 REST 端点不动）；
 * 注册表内为旧类型 DECISION_TABLE/SCORECARD 注册了等价处理器，故既有决策流数据在本引擎下仍可执行。
 *
 * <p>防御：以共享步数计数器（跨递归）防御异常环路，超过 {@link #MAX_STEPS} 上限即终止（R7.6）；
 * 记录实际执行路径供链路追溯。
 */
public class DecisionFlowEngine {

    private static final int MAX_STEPS = 100;

    private final NodeHandlerRegistry registry;
    private final DecisionAggregator aggregator;
    private final AviatorEvaluatorInstance aviator = AviatorEvaluator.newInstance();

    /**
     * 冠军挑战分流的随机源（R8.2）。可注入便于测试：测试可传入固定种子的 {@link Random}
     * 以获得确定性序列，验证大数下各路径频率趋近配置百分比（design Property 5）。
     * 生产默认使用无种子 {@link Random}（等价于按时间播种）。
     */
    private final Random random;

    public DecisionFlowEngine(NodeHandlerRegistry registry, DecisionAggregator aggregator) {
        this(registry, aggregator, new Random());
    }

    /** 供测试注入确定性随机源的构造器（冠军挑战分流用）。 */
    public DecisionFlowEngine(NodeHandlerRegistry registry, DecisionAggregator aggregator, Random random) {
        this.registry = registry;
        this.aggregator = aggregator;
        this.random = random;
    }

    public DecisionFlowResult evaluate(DecisionFlowDef def, Map<String, Object> context) {
        FlowContext ctx = new FlowContext(def, context);
        Walk walk = runTraversal(def, ctx);

        // R9.7：执行到达的 END 节点所配置的决策结果优先作为决策流结果；
        // 历史数据未配置结束决策结果时回退到命中聚合决策。
        String finalDecision = walk.endDecision != null
                ? walk.endDecision
                : aggregator.aggregate(ctx.hits()).name();
        return new DecisionFlowResult(finalDecision, walk.path, ctx.hits(), walk.trace);
    }

    /**
     * 子决策流递归执行（扩展阶段 10.3，R8.5）。
     *
     * <p>由 {@code SubFlowNodeHandler} 在「子决策流节点」中调用，对选中的子流程定义递归执行：
     * <ul>
     *   <li>以父上下文的求值环境 {@code parentEnv} 作为子流程输入（前序节点产出的赋值字段可被子流程引用 R8.5）；</li>
     *   <li>携带子流程递归防护 {@code childGuard}（深度 +1、已访问 id 并入），防御子流程循环（R8.6）；</li>
     *   <li>遍历完成后聚合子流程命中得子决策，将<strong>命中明细</strong>与<strong>子流程新产出的赋值字段</strong>
     *       连同子决策一并返回，交由调用处理器并入父流程累计结果与赋值字段（R8.5）。</li>
     * </ul>
     *
     * @param subDef    子决策流定义
     * @param parentEnv 父流程当前求值环境（作为子流程输入；本方法不修改该 map）
     * @param childGuard 子流程递归防护（由 {@code SubFlowGuard.enter(flowId)} 派生）
     * @return 子流程命中明细 + 赋值字段（含 {@code subFlowDecision}=子流程聚合决策，及子流程新增/变更的 env 键）
     */
    public NodeResult evaluateSubFlow(DecisionFlowDef subDef,
                                      Map<String, Object> parentEnv,
                                      SubFlowGuard childGuard) {
        // 子上下文以父环境为输入（FlowContext 构造内部拷贝，遍历不污染父 env），携带子递归防护
        FlowContext childCtx = new FlowContext(subDef, parentEnv, childGuard);
        runTraversal(subDef, childCtx);

        Decision subDecision = aggregator.aggregate(childCtx.hits());

        // 计算子流程相对父环境「新增/变更」的赋值字段，回并父流程（R8.5）
        Map<String, Object> base = parentEnv == null ? Map.of() : parentEnv;
        Map<String, Object> assignments = new HashMap<>();
        for (Map.Entry<String, Object> e : childCtx.env().entrySet()) {
            Object before = base.get(e.getKey());
            if (before == null ? e.getValue() != null : !before.equals(e.getValue())) {
                assignments.put(e.getKey(), e.getValue());
            }
        }
        assignments.put("subFlowDecision", subDecision.name());
        return new NodeResult(childCtx.hits(), assignments);
    }

    /** 构建遍历状态并从起始节点分治遍历（主遍历与子流程递归共用）。 */
    private Walk runTraversal(DecisionFlowDef def, FlowContext ctx) {
        Map<String, DecisionFlowDef.Node> nodeById = new HashMap<>();
        for (DecisionFlowDef.Node n : def.nodes()) {
            nodeById.put(n.nodeId(), n);
        }

        // 预计算图的入度/出度，用于识别并行网关的分叉（出度>1）与汇聚（入度>1）
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Integer> outDegree = new HashMap<>();
        for (DecisionFlowDef.Edge e : def.edges()) {
            outDegree.merge(e.from(), 1, Integer::sum);
            inDegree.merge(e.to(), 1, Integer::sum);
        }

        Walk walk = new Walk(def, nodeById, inDegree, outDegree, ctx);
        // 从起始节点开始分治遍历（asBranch=false：主遍历不在汇聚网关处提前返回）
        traverse(walk, def.startNodeId(), false);
        return walk;
    }

    /**
     * 分治遍历：从 {@code startId} 沿边推进，支持条件网关单路选择与并行网关分叉-汇聚。
     *
     * @param asBranch 是否为分叉网关派生出的子路径遍历。子路径遍历抵达汇聚网关时停止并上报其 id，
     *                 交由分叉处理器统一汇聚；主遍历（false）不在汇聚网关处提前返回。
     * @return 子路径抵达的配对汇聚网关 id；若到达 END、降级或越界则返回 {@code null}（路径终止）。
     */
    private String traverse(Walk w, String startId, boolean asBranch) {
        String currentId = startId;
        while (currentId != null) {
            if (w.steps++ >= MAX_STEPS) {
                return null; // 步数越界，防御异常环路（R7.6）
            }
            DecisionFlowDef.Node node = w.nodeById.get(currentId);
            if (node == null) {
                return null;
            }

            // 汇聚网关（入度>1）作为分叉子路径的边界：子路径遇到即停，交由分叉处理器汇聚
            boolean isJoin = node.type() == DecisionFlowDef.NodeType.PARALLEL_GATEWAY
                    && degree(w.inDegree, currentId) > 1;
            if (isJoin && asBranch) {
                return currentId;
            }

            // 执行当前节点：命中并入累计结果，赋值字段注入上下文（R9.1）。网关节点处理器返回空结果。
            w.path.add(currentId);
            // R9.4：执行前对输入 env 做快照（含前序节点产出的赋值字段），用于节点级链路记录
            Map<String, Object> inputSnapshot = snapshot(w.ctx.env());
            NodeResult result = registry.dispatch(node, w.ctx);
            for (HitDecision hit : result.hits()) {
                w.ctx.addHit(hit);
            }
            result.assignments().forEach(w.ctx::putAssignment);
            // R9.4：登记本节点链路明细（节点身份 + 输入快照 + 命中 + 赋值字段 + 级联资产产出）
            w.trace.add(toTraceStep(node, inputSnapshot, result));

            if (node.type() == DecisionFlowDef.NodeType.END) {
                // R9.7：决策流执行到达结束节点，记录该节点配置的决策结果作为决策流结果
                String configured = EndDecisionResolver.resolve(node.config());
                if (configured != null && w.endDecision == null) {
                    w.endDecision = configured;
                }
                return null;
            }

            // 并行网关分叉（出度>1）：递归执行各分支至配对汇聚网关，再从汇聚网关继续（R7.3）
            if (node.type() == DecisionFlowDef.NodeType.PARALLEL_GATEWAY
                    && degree(w.outDegree, currentId) > 1) {
                String joinId = null;
                for (DecisionFlowDef.Edge e : outEdges(w.def, currentId)) {
                    String reached = traverse(w, e.to(), true);
                    if (reached != null) {
                        joinId = reached; // 各分支上报的汇聚网关 id 收敛为同一节点
                    }
                }
                if (joinId == null) {
                    return null; // 未找到配对汇聚网关，降级终止该路径
                }
                if (w.steps++ >= MAX_STEPS) {
                    return null;
                }
                // 汇聚网关本身不产决策（仅作路径节点）：记录路径后从其唯一出边串行继续
                DecisionFlowDef.Node joinNode = w.nodeById.get(joinId);
                w.path.add(joinId);
                if (joinNode != null) {
                    Map<String, Object> joinSnapshot = snapshot(w.ctx.env());
                    NodeResult jr = registry.dispatch(joinNode, w.ctx);
                    for (HitDecision hit : jr.hits()) {
                        w.ctx.addHit(hit);
                    }
                    jr.assignments().forEach(w.ctx::putAssignment);
                    w.trace.add(toTraceStep(joinNode, joinSnapshot, jr));
                }
                currentId = serialNext(w.def, joinId, w.ctx.env());
                continue;
            }

            // 条件网关：按出线顺序取第一条满足条件的边；都不满足走默认边；无默认边则终止（降级）
            if (node.type() == DecisionFlowDef.NodeType.CONDITION_GATEWAY) {
                currentId = conditionGatewayNext(w.def, currentId, w.ctx.env());
                continue;
            }

            // 冠军挑战（分流）：按各出线 trafficPercent 加权随机选一条（累积区间+随机数）（R8.1/8.2）
            if (node.type() == DecisionFlowDef.NodeType.CHAMPION_CHALLENGER) {
                currentId = championChallengerNext(w.def, currentId);
                continue;
            }

            // 普通/串行节点：满足条件的首条出边，无条件边兜底（R7.4）
            currentId = serialNext(w.def, currentId, w.ctx.env());
        }
        return null;
    }

    /**
     * 条件网关选路（R7.1/7.2）：按边声明顺序取第一条满足条件的边；都不满足时走默认边
     * （{@code isDefault=true}，取第一条）；无默认边则返回 {@code null}（记录无匹配，降级终止）。
     */
    private String conditionGatewayNext(DecisionFlowDef def, String fromId, Map<String, Object> env) {
        String defaultEdge = null;
        for (DecisionFlowDef.Edge e : outEdges(def, fromId)) {
            if (e.isDefault()) {
                if (defaultEdge == null) {
                    defaultEdge = e.to();
                }
                continue;
            }
            String cond = e.condition();
            if (cond != null && !cond.isBlank() && evalCondition(cond, env)) {
                return e.to();
            }
        }
        return defaultEdge; // 都不满足则走默认边；无默认边为 null（降级）
    }

    /**
     * 冠军挑战分流选路（R8.1/8.2）：按各出线的 {@code trafficPercent} 做加权随机选边。
     *
     * <p>算法（累积区间 + 单次随机数）：设各出边权重为其 {@code trafficPercent}（null 视为 0），
     * 总权重 {@code total = Σ trafficPercent}（保存期校验应 = 100，R8.1）。取随机整数
     * {@code r ∈ [0, total)}，沿边声明顺序累加权重形成区间，{@code r} 落入的首个区间即为命中边。
     * 大数次执行下，各路径被选中频率趋近 {@code trafficPercent / total}（design Property 5）。
     *
     * <p>边界：无出边或总权重 ≤ 0 时返回 {@code null}（无法分流，终止该路径，降级）。
     */
    private String championChallengerNext(DecisionFlowDef def, String fromId) {
        List<DecisionFlowDef.Edge> outs = outEdges(def, fromId);
        int total = 0;
        for (DecisionFlowDef.Edge e : outs) {
            int p = e.trafficPercent() == null ? 0 : e.trafficPercent();
            if (p > 0) {
                total += p;
            }
        }
        if (total <= 0) {
            return null; // 无有效流量配置，无法分流（降级终止）
        }
        int r = random.nextInt(total); // r ∈ [0, total)
        int cumulative = 0;
        for (DecisionFlowDef.Edge e : outs) {
            int p = e.trafficPercent() == null ? 0 : e.trafficPercent();
            if (p <= 0) {
                continue;
            }
            cumulative += p;
            if (r < cumulative) { // 落入 [cumulative-p, cumulative) 区间即命中
                return e.to();
            }
        }
        return null; // 理论不可达（r < total 必落入某区间），兜底返回 null
    }

    /** 串行选路：满足条件的首条出边；无条件边/默认边作为兜底（最后选）。与旧逻辑一致（R7.4）。 */
    private String serialNext(DecisionFlowDef def, String fromId, Map<String, Object> env) {
        String fallback = null;
        for (DecisionFlowDef.Edge e : outEdges(def, fromId)) {
            String cond = e.condition();
            if (cond == null || cond.isBlank()) {
                fallback = e.to(); // 无条件边/默认边记为兜底
                continue;
            }
            if (evalCondition(cond, env)) {
                return e.to();
            }
        }
        return fallback;
    }

    private static List<DecisionFlowDef.Edge> outEdges(DecisionFlowDef def, String fromId) {
        List<DecisionFlowDef.Edge> outs = new ArrayList<>();
        for (DecisionFlowDef.Edge e : def.edges()) {
            if (fromId.equals(e.from())) {
                outs.add(e);
            }
        }
        return outs;
    }

    private static int degree(Map<String, Integer> degreeMap, String nodeId) {
        return degreeMap.getOrDefault(nodeId, 0);
    }

    /**
     * 节点执行前的输入快照（R9.4）。只保留标量/字符串/布尔/数值等「可展示」的值，过滤掉
     * 集合/数组/复杂对象（如策略编码列表），避免把大对象/不可序列化对象带入链路记录。
     */
    private static Map<String, Object> snapshot(Map<String, Object> env) {
        Map<String, Object> snap = new LinkedHashMap<>();
        if (env == null) {
            return snap;
        }
        for (Map.Entry<String, Object> e : env.entrySet()) {
            Object v = e.getValue();
            if (v == null || v instanceof CharSequence || v instanceof Number || v instanceof Boolean
                    || v instanceof Character || v instanceof Enum<?>) {
                snap.put(e.getKey(), v);
            }
            // 其余（集合/Map/自定义对象）不纳入快照，保持链路精简
        }
        return snap;
    }

    /** 由节点与执行结果构建一条节点级链路明细（R9.4）。 */
    private static FlowTraceStep toTraceStep(DecisionFlowDef.Node node,
                                             Map<String, Object> inputSnapshot,
                                             NodeResult result) {
        return new FlowTraceStep(
                node.nodeId(),
                node.type() == null ? null : node.type().name(),
                node.refType(),
                node.refId(),
                inputSnapshot,
                result.hits(),
                result.assignments(),
                result.cascaded());
    }

    private boolean evalCondition(String condition, Map<String, Object> env) {
        try {
            Object r = aviator.execute(condition, env, true);
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    /** 单次执行的遍历状态（节点索引、入/出度、上下文、执行路径、共享步数计数器）。 */
    private static final class Walk {
        private final DecisionFlowDef def;
        private final Map<String, DecisionFlowDef.Node> nodeById;
        private final Map<String, Integer> inDegree;
        private final Map<String, Integer> outDegree;
        private final FlowContext ctx;
        private final List<String> path = new ArrayList<>();
        /** 节点级执行链路明细（R9.4，与 path 同序累积）。子流程在独立 Walk 中遍历，其链路不并入父 Walk。 */
        private final List<FlowTraceStep> trace = new ArrayList<>();
        private int steps;
        /** 执行到达的 END 节点所配置的决策结果（R9.7）；未到达或未配置时为 null。 */
        private String endDecision;

        private Walk(DecisionFlowDef def,
                     Map<String, DecisionFlowDef.Node> nodeById,
                     Map<String, Integer> inDegree,
                     Map<String, Integer> outDegree,
                     FlowContext ctx) {
            this.def = def;
            this.nodeById = nodeById;
            this.inDegree = inDegree;
            this.outDegree = outDegree;
            this.ctx = ctx;
        }
    }
}
