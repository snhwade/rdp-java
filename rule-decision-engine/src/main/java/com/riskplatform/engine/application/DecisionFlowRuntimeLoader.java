package com.riskplatform.engine.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.infrastructure.decisionflow.DecisionFlowReadMapper;
import com.riskplatform.engine.infrastructure.decisionflow.DecisionFlowReadPO;
import com.riskplatform.engine.infrastructure.decisionflow.DecisionFlowVersionReadMapper;
import com.riskplatform.engine.infrastructure.decisionflow.DecisionFlowVersionReadPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按决策流 id 加载可执行定义：优先 ONLINE 版本快照，回退 decision_flow 当前草稿。
 */
@Component
public class DecisionFlowRuntimeLoader {

    private static final Logger log = LoggerFactory.getLogger(DecisionFlowRuntimeLoader.class);

    private final DecisionFlowVersionReadMapper versionMapper;
    private final DecisionFlowReadMapper flowMapper;
    private final DecisionToolDefLoader toolDefLoader;
    private final ObjectMapper objectMapper;

    public DecisionFlowRuntimeLoader(DecisionFlowVersionReadMapper versionMapper,
                                     DecisionFlowReadMapper flowMapper,
                                     DecisionToolDefLoader toolDefLoader,
                                     ObjectMapper objectMapper) {
        this.versionMapper = versionMapper;
        this.flowMapper = flowMapper;
        this.toolDefLoader = toolDefLoader;
        this.objectMapper = objectMapper;
    }

    public LoadedDecisionFlow load(long flowId) {
        DecisionFlowVersionReadPO online = versionMapper.selectOnlineByFlowId(flowId);
        if (online != null && online.getSnapshotJson() != null && !online.getSnapshotJson().isBlank()) {
            DecisionFlowDef def = parseSnapshot(online.getSnapshotJson());
            if (def != null) {
                return new LoadedDecisionFlow(flowId, online.getVersion(), "ONLINE_VERSION", def);
            }
        }
        DecisionFlowReadPO po = flowMapper.selectById(flowId);
        if (po == null) {
            return null;
        }
        if (po.getStatus() != null && "DISABLED".equalsIgnoreCase(po.getStatus())) {
            log.warn("决策流已下线: flowId={}", flowId);
            return null;
        }
        try {
            List<DecisionFlowDef.Node> nodes = objectMapper.readValue(
                    po.getNodesJson(), new TypeReference<List<DecisionFlowDef.Node>>() {});
            List<DecisionFlowDef.Edge> edges = objectMapper.readValue(
                    po.getEdgesJson(), new TypeReference<List<DecisionFlowDef.Edge>>() {});
            DecisionFlowDef def = toolDefLoader.enrich(new DecisionFlowDef(nodes, edges, po.getStartNodeId(),
                    Map.of(), Map.of(), Map.of(), Map.of()));
            return new LoadedDecisionFlow(flowId, null, "DRAFT", def);
        } catch (Exception e) {
            log.warn("决策流定义解析失败: flowId={} {}", flowId, e.getMessage());
            return null;
        }
    }

    private DecisionFlowDef parseSnapshot(String snapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            List<DecisionFlowDef.Node> nodes = objectMapper.convertValue(
                    root.get("nodes"), new TypeReference<List<DecisionFlowDef.Node>>() {});
            List<DecisionFlowDef.Edge> edges = objectMapper.convertValue(
                    root.get("edges"), new TypeReference<List<DecisionFlowDef.Edge>>() {});
            String startNodeId = root.path("startNodeId").asText(null);
            if (nodes == null || edges == null || startNodeId == null) {
                return null;
            }
            return toolDefLoader.enrich(new DecisionFlowDef(nodes, edges, startNodeId,
                    Map.of(), Map.of(), Map.of(), Map.of()));
        } catch (Exception e) {
            log.warn("决策流版本快照解析失败: {}", e.getMessage());
            return null;
        }
    }

    public record LoadedDecisionFlow(long flowId, Integer version, String source, DecisionFlowDef definition) {
    }
}
