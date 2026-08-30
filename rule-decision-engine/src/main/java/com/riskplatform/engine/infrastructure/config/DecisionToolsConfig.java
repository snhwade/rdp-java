package com.riskplatform.engine.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.domain.decision.DecisionAggregator;
import com.riskplatform.engine.application.IndicatorContextEnricher;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowEngine;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowEvaluator;
import com.riskplatform.engine.domain.decisionflow.node.DecisionToolNodeHandler;
import com.riskplatform.engine.domain.decisionflow.node.GatewayNodeHandler;
import com.riskplatform.engine.domain.decisionflow.node.ListCheckNodeHandler;
import com.riskplatform.engine.domain.decisionflow.node.ModelNodeHandler;
import com.riskplatform.engine.domain.decisionflow.node.ChampionChallengerHandler;
import com.riskplatform.engine.domain.decisionflow.node.NodeHandler;
import com.riskplatform.engine.domain.decisionflow.node.NodeHandlerRegistry;
import com.riskplatform.engine.domain.decisionflow.node.RulePackageNodeHandler;
import com.riskplatform.engine.domain.decisionflow.node.StartEndNodeHandler;
import com.riskplatform.engine.domain.decisionflow.node.SubFlowNodeHandler;
import com.riskplatform.engine.domain.decisionflow.SubFlowDefinitionPort;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.domain.decisionmatrix.DecisionMatrixEvaluator;
import com.riskplatform.engine.domain.decisiontable.DecisionTableEvaluator;
import com.riskplatform.engine.domain.decisiontree.DecisionTreeEvaluator;
import com.riskplatform.engine.domain.list.ListCheckPort;
import com.riskplatform.engine.domain.model.ModelScorePort;
import com.riskplatform.engine.domain.rule.RuleExecutor;
import com.riskplatform.engine.domain.rulepackage.RiskLevelDecisionMapper;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinitionPort;
import com.riskplatform.engine.domain.rulepackage.RulePackageExecutor;
import com.riskplatform.engine.domain.score.ScoreCalculator;
import com.riskplatform.engine.domain.scorecard.ScorecardEvaluator;
import com.riskplatform.engine.domain.strategy.StrategyAggregator;
import com.riskplatform.engine.infrastructure.client.AiScoreClient;
import com.riskplatform.engine.infrastructure.client.RestListCheckClient;
import com.riskplatform.engine.infrastructure.standalone.ContextOnlyListCheckPort;
import com.riskplatform.engine.infrastructure.standalone.UnavailableModelScorePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 决策工具装配（S2 决策表、S3 评分卡、S4 决策流、S8 决策树、S9 决策矩阵）。
 */
@Configuration
public class DecisionToolsConfig {

    @Bean
    public DecisionAggregator decisionAggregator() {
        return new DecisionAggregator();
    }

    @Bean
    public DecisionTableEvaluator decisionTableEvaluator() {
        return new DecisionTableEvaluator();
    }

    @Bean
    public ScorecardEvaluator scorecardEvaluator() {
        return new ScorecardEvaluator();
    }

    @Bean
    public DecisionTreeEvaluator decisionTreeEvaluator() {
        return new DecisionTreeEvaluator();
    }

    @Bean
    public DecisionMatrixEvaluator decisionMatrixEvaluator() {
        return new DecisionMatrixEvaluator();
    }

    @Bean
    public DecisionFlowEvaluator decisionFlowEvaluator(DecisionTableEvaluator decisionTableEvaluator,
                                                       ScorecardEvaluator scorecardEvaluator,
                                                       DecisionAggregator decisionAggregator) {
        return new DecisionFlowEvaluator(decisionTableEvaluator, scorecardEvaluator, decisionAggregator);
    }

    /**
     * AI 在线评分客户端（模型节点用，R6.4）。基址可经配置覆盖，默认指向本地 ai-training-service:8000。
     */
    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public ModelScorePort remoteAiScoreClient(
            @Value("${downstream.ai-training:http://localhost:8000}") String aiTrainingBaseUrl) {
        return new AiScoreClient(RestClient.create(), aiTrainingBaseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
    public ModelScorePort standaloneAiScoreClient() {
        return new UnavailableModelScorePort();
    }

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public ListCheckPort remoteListCheckPort(
            @Value("${downstream.screening:http://localhost:8085}") String screeningBaseUrl) {
        return new RestListCheckClient(RestClient.create(), screeningBaseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
    public ListCheckPort standaloneListCheckPort() {
        return new ContextOnlyListCheckPort();
    }

    /**
     * 规则包节点在线执行器（复用 6.2 执行内核）。与试运行的 {@code dryRunRulePackageExecutor} 区分命名，
     * 两者实现一致但用途不同（在线规则包节点 vs 试运行影子评估）。
     */
    @Bean
    public RulePackageExecutor onlineRulePackageExecutor(RuleExecutor ruleExecutor,
                                                         StrategyAggregator strategyAggregator,
                                                         ScoreCalculator scoreCalculator,
                                                         DecisionAggregator decisionAggregator) {
        return new RulePackageExecutor(ruleExecutor, strategyAggregator, scoreCalculator,
                decisionAggregator, RiskLevelDecisionMapper.DEFAULT);
    }

    /**
     * 节点处理器注册表（扩展阶段）。本任务（9.3）在 9.2 基础上新增注册：
     * <ul>
     *   <li>规则包节点 {@link RulePackageNodeHandler}（调 {@link RulePackageExecutor}）；</li>
     *   <li>模型节点 {@link ModelNodeHandler}（调 {@link ModelScorePort}，不可用降级）；</li>
     *   <li>决策工具节点 {@link DecisionToolNodeHandler} 增强为覆盖 决策表/评分卡/决策树/决策矩阵 四类。</li>
     * </ul>
     * 网关/分流节点处理器在 10.1/10.2 注册；子流程处理器在 10.3 注册（以 ObjectProvider 延迟注入引擎打破循环）。
     */
    @Bean
    public NodeHandlerRegistry nodeHandlerRegistry(DecisionTableEvaluator decisionTableEvaluator,
                                                   ScorecardEvaluator scorecardEvaluator,
                                                   DecisionTreeEvaluator decisionTreeEvaluator,
                                                   DecisionMatrixEvaluator decisionMatrixEvaluator,
                                                   RulePackageDefinitionPort rulePackageDefinitionPort,
                                                   RulePackageExecutor onlineRulePackageExecutor,
                                                   IndicatorContextEnricher indicatorContextEnricher,
                                                   ModelScorePort modelScorePort,
                                                   ListCheckPort listCheckPort,
                                                   SubFlowDefinitionPort subFlowDefinitionPort,
                                                   ObjectProvider<DecisionFlowEngine> decisionFlowEngineProvider,
                                                   ObjectMapper objectMapper) {
        List<NodeHandler> handlers = List.of(
                new StartEndNodeHandler(DecisionFlowDef.NodeType.START),
                new StartEndNodeHandler(DecisionFlowDef.NodeType.END),
                new ListCheckNodeHandler(listCheckPort),
                // 决策工具节点：覆盖 决策表/评分卡/决策树/决策矩阵 四类（9.3）
                new DecisionToolNodeHandler(DecisionFlowDef.NodeType.DECISION_TOOL,
                        decisionTableEvaluator, scorecardEvaluator,
                        decisionTreeEvaluator, decisionMatrixEvaluator),
                // 规则包节点（9.3）
                new RulePackageNodeHandler(rulePackageDefinitionPort, onlineRulePackageExecutor, indicatorContextEnricher),
                // 模型节点（9.3，AI 在线评分 + 降级）
                new ModelNodeHandler(modelScorePort, objectMapper),
                // 网关节点（10.1）：纯路径节点，选路由引擎遍历器实现
                //  - 条件网关：出线顺序取第一条满足，无满足走默认边（R7.1/7.2）
                //  - 并行网关：分叉并行执行各路径、汇聚合并后继续（R7.3）
                new GatewayNodeHandler(DecisionFlowDef.NodeType.CONDITION_GATEWAY),
                new GatewayNodeHandler(DecisionFlowDef.NodeType.PARALLEL_GATEWAY),
                // 冠军挑战（分流）节点（10.2）：纯路径节点，按出线 trafficPercent 加权随机选边由引擎遍历器实现（R8.1/8.2）
                new ChampionChallengerHandler(),
                // 子决策流节点（10.3）：候选选择（事件+机构唯一匹配，无匹配走默认）+ 递归执行 + 递归防护（R8.3/8.4/8.5/8.6）
                //  Engine↔Handler 循环依赖：以 ObjectProvider::getObject 作为 Supplier 延迟注入引擎，运行期才解析，打破构造期循环
                new SubFlowNodeHandler(subFlowDefinitionPort,
                        decisionFlowEngineProvider::getObject, objectMapper),
                // 兼容旧节点类型：等价处理器，保证既有决策流数据可执行
                new DecisionToolNodeHandler(DecisionFlowDef.NodeType.DECISION_TABLE,
                        decisionTableEvaluator, scorecardEvaluator,
                        decisionTreeEvaluator, decisionMatrixEvaluator),
                new DecisionToolNodeHandler(DecisionFlowDef.NodeType.SCORECARD,
                        decisionTableEvaluator, scorecardEvaluator,
                        decisionTreeEvaluator, decisionMatrixEvaluator));
        return new NodeHandlerRegistry(handlers);
    }

    /**
     * 决策流引擎（演进自 {@link DecisionFlowEvaluator}）。与旧执行器并存，旧执行路径保持可用。
     */
    @Bean
    public DecisionFlowEngine decisionFlowEngine(NodeHandlerRegistry nodeHandlerRegistry,
                                                 DecisionAggregator decisionAggregator) {
        return new DecisionFlowEngine(nodeHandlerRegistry, decisionAggregator);
    }
}
