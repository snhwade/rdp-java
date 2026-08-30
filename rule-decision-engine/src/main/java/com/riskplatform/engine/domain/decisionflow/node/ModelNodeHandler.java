package com.riskplatform.engine.domain.decisionflow.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.domain.model.ModelScorePort;
import com.riskplatform.engine.domain.model.ModelScoreResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型节点处理器（MODEL，扩展阶段 R6.4）。
 *
 * <p>节点引用某模型（{@code node.refType()}/{@code node.refId()}）。本处理器经 {@link ModelScorePort}
 * 调用 ai-training-service 在线评分，把模型输出登记为赋值字段供后续节点引用（R9.1/R9.2）。
 *
 * <h3>赋值字段登记（R9.1/R9.2）</h3>
 * <p>模型输出以 {@code model_<ref>} 系列键登记，{@code <ref>} 取节点 refId（缺省取 refType）：
 * <ul>
 *   <li>{@code model_<ref>}：模型分值（可用时为模型分，不可用时为降级默认值）；</li>
 *   <li>{@code model_<ref>_label}：模型标签/等级（可用且模型给出时）；</li>
 *   <li>{@code model_<ref>_available}：模型是否可用（boolean，便于后续节点条件判断是否走降级分支）；</li>
 *   <li>{@code lastScore}：最近模型/评分节点分值（与评分卡/评分包统一约定）。</li>
 * </ul>
 *
 * <h3>降级策略（R6.4）</h3>
 * <p>模型不可用（端点未实现/超时/异常，{@link ModelScoreResult#available()}=false）时，按节点配置 JSON
 * （{@code node.config()}）的降级配置处理：
 * <pre>
 *   { "defaultScore": 0.0, "defaultLabel": "UNKNOWN" }
 * </pre>
 * 缺省 defaultScore=0.0、defaultLabel=null。无论是否降级都登记赋值字段，并记录不可用原因。
 * 模型节点本身不直接产出命中决策（决策由后续引用 model_* 赋值字段的规则/网关节点做出）。
 */
public final class ModelNodeHandler implements NodeHandler {

    private static final Logger log = LoggerFactory.getLogger(ModelNodeHandler.class);

    private final ModelScorePort modelScorePort;
    private final ObjectMapper objectMapper;

    public ModelNodeHandler(ModelScorePort modelScorePort, ObjectMapper objectMapper) {
        this.modelScorePort = modelScorePort;
        this.objectMapper = objectMapper;
    }

    @Override
    public DecisionFlowDef.NodeType supportedType() {
        return DecisionFlowDef.NodeType.MODEL;
    }

    @Override
    public NodeResult handle(DecisionFlowDef.Node node, FlowContext ctx) {
        String ref = resolveRef(node);
        if (ref == null) {
            log.warn("模型节点未配置 refId/refType，按降级处理: nodeId={}", node.nodeId());
            return NodeResult.empty();
        }

        DegradeConfig degrade = parseDegradeConfig(node.config());
        ModelScoreResult result = modelScorePort.score(ref, ctx.env());

        Map<String, Object> assignments = new HashMap<>();
        String key = "model_" + ref;
        if (result.available()) {
            assignments.put(key, result.score());
            if (result.label() != null) {
                assignments.put(key + "_label", result.label());
            }
            assignments.put(key + "_available", true);
            assignments.put("lastScore", result.score());
        } else {
            // R6.4 降级：产出节点配置的默认值并记录原因
            assignments.put(key, degrade.defaultScore());
            if (degrade.defaultLabel() != null) {
                assignments.put(key + "_label", degrade.defaultLabel());
            }
            assignments.put(key + "_available", false);
            assignments.put("lastScore", degrade.defaultScore());
            log.warn("模型节点降级: nodeId={} modelRef={} defaultScore={} 原因={}",
                    node.nodeId(), ref, degrade.defaultScore(), result.reason());
        }
        return new NodeResult(java.util.List.of(), assignments);
    }

    /** 模型引用标识：优先 refId（数值），缺省用 refType（编码）。 */
    private String resolveRef(DecisionFlowDef.Node node) {
        if (node.refId() != null) {
            return String.valueOf(node.refId());
        }
        String refType = node.refType();
        if (refType != null && !refType.isBlank()) {
            return refType.trim();
        }
        return null;
    }

    /** 解析节点降级配置（缺省 defaultScore=0.0、defaultLabel=null）。 */
    private DegradeConfig parseDegradeConfig(String config) {
        double defaultScore = 0.0;
        String defaultLabel = null;
        if (config != null && !config.isBlank()) {
            try {
                Map<String, Object> m = objectMapper.readValue(config, Map.class);
                if (m != null) {
                    if (m.get("defaultScore") instanceof Number n) {
                        defaultScore = n.doubleValue();
                    }
                    Object dl = m.get("defaultLabel");
                    if (dl != null) {
                        defaultLabel = String.valueOf(dl);
                    }
                }
            } catch (Exception e) {
                log.warn("模型节点降级配置解析失败，按默认值处理: 原因={}", e.getMessage());
            }
        }
        return new DegradeConfig(defaultScore, defaultLabel);
    }

    /** 节点降级配置（R6.4）。 */
    private record DegradeConfig(double defaultScore, String defaultLabel) {
    }
}
