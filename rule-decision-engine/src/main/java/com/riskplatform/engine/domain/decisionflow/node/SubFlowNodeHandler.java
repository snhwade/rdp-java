package com.riskplatform.engine.domain.decisionflow.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowEngine;
import com.riskplatform.engine.domain.decisionflow.SubFlowDefinitionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 子决策流节点处理器（SUB_FLOW，扩展阶段 10.3，R8.3/8.4/8.5/8.6）。
 *
 * <p>子决策流节点用于在父决策流中引用并复用一个子决策流。本处理器：
 * <ol>
 *   <li><b>候选选择（R8.4）</b>：从节点配置 {@code node.config()} 解析候选子流程列表，
 *       按「决策事件 + 适用机构」从父上下文唯一匹配一个候选；无匹配则走默认子流程（兜底）；
 *       既无匹配也无默认则记录未匹配并降级（返回空结果）。无候选配置时退化为单引用
 *       {@code node.refId()}。</li>
 *   <li><b>加载（R8.5）</b>：经 {@link SubFlowDefinitionPort} 只读加载选中子流程定义；
 *       不存在/已下线时降级。</li>
 *   <li><b>递归防护（R8.6）</b>：用 {@link FlowContext#subFlowGuard()} 判定可否下钻
 *       （递归深度未越界且未形成环），不可进入则降级；可进入时 {@code enter(flowId)} 派生新防护
 *       传入递归执行。</li>
 *   <li><b>递归执行与结果合并（R8.5）</b>：调用 {@link DecisionFlowEngine#evaluateSubFlow}，
 *       把子流程命中并入父流程累计结果（{@link NodeResult#hits()}），把子流程新产出的赋值字段
 *       连同 {@code subFlowDecision} 登记为父流程赋值字段（供后续节点引用）。</li>
 * </ol>
 *
 * <h3>节点配置 JSON 约定（R8.4）</h3>
 * <pre>
 * {
 *   "candidates": [
 *     { "flowId": 10, "eventCodes": ["TXN_PAY"], "orgIds": [4, 5] },
 *     { "flowId": 11, "eventCodes": ["TXN_REFUND"], "orgIds": [] }
 *   ],
 *   "defaultFlowId": 99
 * }
 * </pre>
 * 候选匹配规则：候选的 {@code eventCodes} 为空视为「匹配任意事件」，否则须包含上下文事件编码；
 * {@code orgIds} 为空视为「匹配任意机构」，否则须包含上下文机构 id。事件 + 机构同时满足才算命中。
 * 命中候选<strong>唯一</strong>时执行该候选；命中 0 个或多于 1 个（不唯一）均走 {@code defaultFlowId}。
 *
 * <h3>上下文键约定</h3>
 * <p>事件编码取 env 的 {@code eventTypeCode}（兼容 {@code eventType}）；机构 id 取 env 的
 * {@code orgId}（兼容 {@code applicableOrgId}）。缺失时按「未提供」处理（仅能匹配空约束候选）。
 *
 * <h3>Engine↔Handler 循环依赖</h3>
 * <p>本处理器需调用 {@link DecisionFlowEngine#evaluateSubFlow} 递归执行子流程，而引擎又依赖
 * {@link NodeHandlerRegistry}（含本处理器）→ 形成构造期循环依赖。故此处<strong>不直接持有引擎实例</strong>，
 * 而以 {@link Supplier}{@code <DecisionFlowEngine>} <strong>延迟注入</strong>打破循环：装配期仅传入
 * 取值函数，运行期（首次执行子流程节点时）才解析出引擎单例。此时两个 Bean 均已创建完毕，不再相互阻塞。
 */
public final class SubFlowNodeHandler implements NodeHandler {

    private static final Logger log = LoggerFactory.getLogger(SubFlowNodeHandler.class);

    private final SubFlowDefinitionPort definitionPort;
    /** 延迟解析的引擎供给器：打破 Engine↔Handler 构造期循环依赖（见类注释）。 */
    private final Supplier<DecisionFlowEngine> engineSupplier;
    private final ObjectMapper objectMapper;

    public SubFlowNodeHandler(SubFlowDefinitionPort definitionPort,
                              Supplier<DecisionFlowEngine> engineSupplier,
                              ObjectMapper objectMapper) {
        this.definitionPort = definitionPort;
        this.engineSupplier = engineSupplier;
        this.objectMapper = objectMapper;
    }

    @Override
    public DecisionFlowDef.NodeType supportedType() {
        return DecisionFlowDef.NodeType.SUB_FLOW;
    }

    @Override
    public NodeResult handle(DecisionFlowDef.Node node, FlowContext ctx) {
        Long flowId = selectSubFlowId(node, ctx.env());
        if (flowId == null) {
            // 无匹配且无默认子流程：记录未匹配并降级（R8.4）
            log.warn("子决策流节点无匹配子流程且无默认子流程，按降级处理: nodeId={}", node.nodeId());
            return NodeResult.empty();
        }

        // 递归防护（R8.6）：深度越界或形成环则拒绝下钻（降级）
        SubFlowGuard guard = ctx.subFlowGuard();
        if (!guard.canEnter(flowId)) {
            log.warn("子决策流递归防护触发（深度越界或循环引用），按降级处理: nodeId={} flowId={} depth={} visited={}",
                    node.nodeId(), flowId, guard.depth(), guard.visitedFlowIds());
            return NodeResult.empty();
        }

        DecisionFlowDef subDef = definitionPort.load(flowId);
        if (subDef == null) {
            // 子流程不存在/已下线/解析失败：运行期降级（R8.4/R6.6）
            log.warn("子决策流节点引用的子流程不可用，按降级处理: nodeId={} flowId={}", node.nodeId(), flowId);
            return NodeResult.empty();
        }

        // 进入子流程：派生携带「深度+1、flowId 并入访问集合」的新防护，递归执行（R8.5/R8.6）
        SubFlowGuard childGuard = guard.enter(flowId);
        return engineSupplier.get().evaluateSubFlow(subDef, ctx.env(), childGuard);
    }

    /**
     * 候选选择（R8.4）：按「决策事件 + 适用机构」从候选列表唯一匹配；无唯一匹配走默认；
     * 无候选配置时退化为单引用 {@code node.refId()}；最终无可执行子流程返回 {@code null}。
     */
    private Long selectSubFlowId(DecisionFlowDef.Node node, Map<String, Object> env) {
        SubFlowConfig config = parseConfig(node.config());
        String eventCode = stringValue(env.get("eventTypeCode"), env.get("eventType"));
        Long orgId = longValue(env.get("orgId"), env.get("applicableOrgId"));

        if (config == null || config.candidates() == null || config.candidates().isEmpty()) {
            // 无候选配置：单引用语义（直接用节点 refId）
            return node.refId();
        }

        List<Long> matched = new ArrayList<>();
        for (Candidate c : config.candidates()) {
            if (c == null || c.flowId() == null) {
                continue;
            }
            if (matches(c, eventCode, orgId)) {
                matched.add(c.flowId());
            }
        }
        if (matched.size() == 1) {
            return matched.get(0); // 唯一匹配
        }
        // 0 个或多个匹配（不唯一）：走默认子流程（兜底）；无默认则 node.refId() 兜底；再无则 null
        if (config.defaultFlowId() != null) {
            return config.defaultFlowId();
        }
        return node.refId();
    }

    /** 候选匹配：eventCodes 空=匹配任意事件，否则须包含上下文事件；orgIds 同理。 */
    private boolean matches(Candidate c, String eventCode, Long orgId) {
        boolean eventOk = c.eventCodes() == null || c.eventCodes().isEmpty()
                || (eventCode != null && c.eventCodes().contains(eventCode));
        boolean orgOk = c.orgIds() == null || c.orgIds().isEmpty()
                || (orgId != null && c.orgIds().contains(orgId));
        return eventOk && orgOk;
    }

    private SubFlowConfig parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(config, SubFlowConfig.class);
        } catch (Exception e) {
            log.warn("子决策流节点配置解析失败，退化为单引用: 原因={}", e.getMessage());
            return null;
        }
    }

    private static String stringValue(Object... candidates) {
        for (Object o : candidates) {
            if (o != null) {
                String s = String.valueOf(o);
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static Long longValue(Object... candidates) {
        for (Object o : candidates) {
            if (o == null) {
                continue;
            }
            if (o instanceof Number n) {
                return n.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
                // 尝试下一个候选键
            }
        }
        return null;
    }

    /** 子决策流节点配置（R8.4）。{@code @JsonIgnoreProperties} 容忍未知字段（前端可能多传）。 */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record SubFlowConfig(List<Candidate> candidates, Long defaultFlowId) {
    }

    /** 候选子流程：flowId + 适用事件编码集合 + 适用机构 id 集合（空集合表示不限定）。 */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record Candidate(Long flowId, List<String> eventCodes, List<Long> orgIds) {
    }
}
