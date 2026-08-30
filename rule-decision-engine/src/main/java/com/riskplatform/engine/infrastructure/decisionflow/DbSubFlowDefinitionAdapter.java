package com.riskplatform.engine.infrastructure.decisionflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.domain.decisionflow.SubFlowDefinitionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 子决策流定义在线加载适配器（扩展阶段 10.3，R8.5）。
 *
 * <p>实现 {@link SubFlowDefinitionPort}：按决策流 id 从 MySQL（rule-config 拥有的 {@code decision_flow}
 * 表，引擎共享同一库）只读 {@code nodes_json}/{@code edges_json}/{@code start_node_id}，反序列化为
 * {@link DecisionFlowDef} 供 {@code DecisionFlowEngine} 递归执行（参考 {@code DbRulePackageDefinitionAdapter}
 * 同款只读 DAO 思路，6.2/7.2）。
 *
 * <p><b>JSON 兼容</b>：config 侧以 {@code DecisionFlow.Node}/{@code DecisionFlow.Edge} 序列化，其字段
 * （nodeId/type/refType/refId/config 与 from/to/condition/trafficPercent/isDefault）与引擎侧
 * {@link DecisionFlowDef.Node}/{@link DecisionFlowDef.Edge} 一一对应，故可直接反序列化为引擎记录。
 *
 * <p><b>降级（R8.4/R6.6）</b>：流程不存在、已下线、或 JSON 解析失败时返回 {@code null}，由
 * {@code SubFlowNodeHandler} 按运行期降级处理（记录未匹配/不可用，不中断父决策流）。
 *
 * <p><b>已知边界</b>：子流程内若含「决策工具节点」（决策表/评分卡/决策树/决策矩阵），其内联定义映射
 * 此处置空（仅决策流执行入口注入内联工具定义），此类节点在子流程内按运行期降级处理（空结果，不中断）。
 * 规则包/模型/网关/分流/子流程节点不受影响。
 */
@Component
public class DbSubFlowDefinitionAdapter implements SubFlowDefinitionPort {

    private static final Logger log = LoggerFactory.getLogger(DbSubFlowDefinitionAdapter.class);

    private final DecisionFlowReadMapper flowMapper;
    private final ObjectMapper objectMapper;
    private final com.riskplatform.engine.infrastructure.configcache.ConfigCacheRegistry configCache;

    public DbSubFlowDefinitionAdapter(DecisionFlowReadMapper flowMapper,
                                      ObjectMapper objectMapper,
                                      com.riskplatform.engine.infrastructure.configcache.ConfigCacheRegistry configCache) {
        this.flowMapper = flowMapper;
        this.objectMapper = objectMapper;
        this.configCache = configCache;
    }

    @Override
    public DecisionFlowDef load(long flowId) {
        return configCache.getOrLoad("DECISION_FLOW", String.valueOf(flowId), id -> loadUncached(Long.parseLong(id)));
    }

    private DecisionFlowDef loadUncached(long flowId) {
        DecisionFlowReadPO po = flowMapper.selectById(flowId);
        if (po == null) {
            log.warn("子决策流节点引用的决策流不存在: flowId={}", flowId);
            return null;
        }
        // 已下线子流程：运行期降级（返回 null，由节点处理器记录原因 R8.4/R6.6）
        if (po.getStatus() != null && "DISABLED".equalsIgnoreCase(po.getStatus())) {
            log.warn("子决策流节点引用的决策流已下线: flowId={}", flowId);
            return null;
        }
        try {
            List<DecisionFlowDef.Node> nodes = objectMapper.readValue(
                    po.getNodesJson(), new TypeReference<List<DecisionFlowDef.Node>>() {});
            List<DecisionFlowDef.Edge> edges = objectMapper.readValue(
                    po.getEdgesJson(), new TypeReference<List<DecisionFlowDef.Edge>>() {});
            // 决策工具内联定义映射置空（见类注释「已知边界」），其余节点类型不受影响
            return new DecisionFlowDef(nodes, edges, po.getStartNodeId(),
                    Map.of(), Map.of(), Map.of(), Map.of());
        } catch (Exception e) {
            log.warn("子决策流定义解析失败，按降级处理: flowId={} 原因={}", flowId, e.getMessage());
            return null;
        }
    }
}
