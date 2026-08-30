package com.riskplatform.engine.domain.decisionflow.node;

import com.riskplatform.engine.application.IndicatorContextEnricher;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinition;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinitionPort;
import com.riskplatform.engine.domain.rulepackage.RulePackageExecutor;
import com.riskplatform.engine.domain.rulepackage.RulePackageResult;
import com.riskplatform.engine.domain.rulepackage.TriggerMode;
import com.riskplatform.engine.domain.strategy.StrategyItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则包节点处理器（RULE_PACKAGE，扩展阶段 R6.2）。
 *
 * <p>节点通过 {@code node.refId()} 引用某规则包。本处理器：
 * <ol>
 *   <li>经 {@link RulePackageDefinitionPort} 从配置库加载规则包<strong>完整</strong>执行定义
 *       （含命中模式规则绑定策略 / 评分模式区间绑定策略）；</li>
 *   <li>用 {@link RulePackageExecutor} 按触发模式（命中/评分）执行；</li>
 *   <li>把命中规则决策并入决策流累计结果（{@link NodeResult#hits()}），把规则包输出策略与
 *       关键产出登记为赋值字段供后续节点引用（{@link NodeResult#assignments()}）。</li>
 * </ol>
 *
 * <p><b>赋值字段登记（R9.1/R9.2）</b>：
 * <ul>
 *   <li>{@code lastDecision}：规则包决策结论（PASS/REVIEW/REJECT）；</li>
 *   <li>{@code rulePackage_<refId>_decision}：本规则包决策（带 refId 命名，避免多节点覆盖）；</li>
 *   <li>{@code rulePackage_<refId>_strategies}：本规则包聚合输出的策略编码列表；</li>
 *   <li>评分模式额外登记 {@code lastScore}/{@code lastLevel} 及
 *       {@code rulePackage_<refId>_score}/{@code _level}/{@code _warn}。</li>
 * </ul>
 *
 * <p><b>运行期降级（R6.4/R6.6）</b>：规则包不存在/已下线（端口返回 null）时返回空结果，
 * 不产决策、不中断决策流，并记录原因。
 */
public final class RulePackageNodeHandler implements NodeHandler {

    private static final Logger log = LoggerFactory.getLogger(RulePackageNodeHandler.class);

    private final RulePackageDefinitionPort definitionPort;
    private final RulePackageExecutor rulePackageExecutor;
    private final IndicatorContextEnricher indicatorEnricher;

    public RulePackageNodeHandler(RulePackageDefinitionPort definitionPort,
                                  RulePackageExecutor rulePackageExecutor,
                                  IndicatorContextEnricher indicatorEnricher) {
        this.definitionPort = definitionPort;
        this.rulePackageExecutor = rulePackageExecutor;
        this.indicatorEnricher = indicatorEnricher;
    }

    @Override
    public DecisionFlowDef.NodeType supportedType() {
        return DecisionFlowDef.NodeType.RULE_PACKAGE;
    }

    @Override
    public NodeResult handle(DecisionFlowDef.Node node, FlowContext ctx) {
        Long refId = node.refId();
        if (refId == null) {
            log.warn("规则包节点未配置 refId，按降级处理: nodeId={}", node.nodeId());
            return NodeResult.empty();
        }

        RulePackageDefinition definition = definitionPort.load(refId);
        if (definition == null) {
            // 引用资产不存在或已下线：运行期降级（R6.4/R6.6）
            log.warn("规则包节点引用资产不可用，按降级处理: nodeId={} refId={}", node.nodeId(), refId);
            return NodeResult.empty();
        }

        indicatorEnricher.enrichFromExpressions(
                definition.rules().stream().map(r -> r.expression()).toList(), ctx.env());

        RulePackageResult result = rulePackageExecutor.execute(definition, ctx.env());

        Map<String, Object> assignments = new HashMap<>();
        // 规则包决策并入累计结果，并登记赋值字段（R6.2/R9.1）
        if (result.decision() != null) {
            assignments.put("lastDecision", result.decision().name());
            assignments.put("rulePackage_" + refId + "_decision", result.decision().name());
        }
        // 输出策略登记为赋值字段（策略编码列表，供后续节点引用；策略只记录不下发）
        assignments.put("rulePackage_" + refId + "_strategies", strategyCodes(result.strategies()));

        if (result.triggerMode() == TriggerMode.SCORE) {
            if (result.score() != null) {
                assignments.put("lastScore", result.score());
                assignments.put("rulePackage_" + refId + "_score", result.score());
            }
            if (result.riskLevelCode() != null) {
                assignments.put("lastLevel", result.riskLevelCode());
                assignments.put("rulePackage_" + refId + "_level", result.riskLevelCode());
            }
            assignments.put("rulePackage_" + refId + "_warn", result.warnGenerated());
        }

        return new NodeResult(result.hitRules(), assignments);
    }

    /** 提取策略编码列表（确定性顺序，便于后续节点/链路记录引用）。 */
    private List<String> strategyCodes(List<StrategyItem> strategies) {
        if (strategies == null) {
            return List.of();
        }
        return strategies.stream()
                .filter(s -> s != null && s.strategyCode() != null)
                .map(StrategyItem::strategyCode)
                .toList();
    }
}
