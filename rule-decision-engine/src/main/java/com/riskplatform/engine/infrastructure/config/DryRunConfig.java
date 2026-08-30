package com.riskplatform.engine.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.crypto.FieldCryptoConfig;
import com.riskplatform.engine.application.DryRunAsyncRunner;
import com.riskplatform.engine.application.DryRunService;
import com.riskplatform.engine.domain.decision.DecisionAggregator;
import com.riskplatform.engine.domain.dryrun.DryRunJobRepository;
import com.riskplatform.engine.domain.dryrun.DryRunSampleSourcePort;
import com.riskplatform.engine.domain.dryrun.DryRunTargetPort;
import com.riskplatform.engine.domain.rule.RuleExecutor;
import com.riskplatform.engine.domain.rulepackage.RiskLevelDecisionMapper;
import com.riskplatform.engine.domain.rulepackage.RulePackageExecutor;
import com.riskplatform.engine.domain.score.ScoreCalculator;
import com.riskplatform.engine.domain.strategy.StrategyAggregator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 试运行（影子模式）装配（R5.1–R5.6 / R14.3）。
 *
 * <p>装配试运行子域所需 Bean，并<strong>启用 {@code @Async}</strong>：
 * <ul>
 *   <li>{@link EnableAsync}：本工程此前未启用异步，于本配置类启用（仅本子域使用 @Async，
 *       影响范围最小化；如后续其他子域也需异步可继续复用）；</li>
 *   <li>{@code dryRunExecutor}：试运行<strong>独立线程池</strong>，与在线决策链路隔离，
 *       异步空跑不阻塞在线决策（R14.3）；</li>
 *   <li>{@link RulePackageExecutor}：复用规则包执行内核（命中/评分）逐条评估样本（6.2 已实现）；</li>
 *   <li>{@link DryRunAsyncRunner}/{@link DryRunService}：异步执行器与发起入口。</li>
 * </ul>
 *
 * <p>引入 {@link FieldCryptoConfig}：试运行从 risk_order 读取历史订单上下文（落库时由
 * decision-gateway AES-256-GCM 加密 R17.4），需装配字段加解密器以透明解密读取。
 *
 * <p><b>隔离保证（R5.2/R5.6）</b>：本配置<strong>不</strong>向 {@link DryRunAsyncRunner} 注入任何
 * 在线副作用组件（DecisionLogService / DecisionStrategyOutputService / DecisionMetrics），
 * 因此试运行不写 decision_log、不输出真实策略、不参与指标累计与告警。
 */
@Configuration
@EnableAsync
@Import(FieldCryptoConfig.class)
public class DryRunConfig {

    /**
     * 试运行独立线程池（R14.3）。
     *
     * <p>与在线决策链路彻底隔离：核心线程 2、最大 4、队列 100，拒绝策略 CallerRuns（极端高负载时
     * 由提交线程兜底执行，避免任务静默丢失）。线程名前缀便于排查。
     */
    @Bean("dryRunExecutor")
    public Executor dryRunExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("dry-run-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 规则包执行器（复用 6.2 执行内核）。试运行逐条评估目标规则/规则包。
     */
    @Bean
    public RulePackageExecutor dryRunRulePackageExecutor(RuleExecutor ruleExecutor,
                                                         StrategyAggregator strategyAggregator,
                                                         ScoreCalculator scoreCalculator,
                                                         DecisionAggregator decisionAggregator) {
        return new RulePackageExecutor(ruleExecutor, strategyAggregator, scoreCalculator,
                decisionAggregator, RiskLevelDecisionMapper.DEFAULT);
    }

    @Bean
    public StrategyAggregator strategyAggregator() {
        return new StrategyAggregator();
    }

    @Bean
    public ScoreCalculator scoreCalculator() {
        return new ScoreCalculator();
    }

    @Bean
    public DryRunAsyncRunner dryRunAsyncRunner(DryRunJobRepository jobRepository,
                                               DryRunTargetPort targetPort,
                                               DryRunSampleSourcePort sampleSourcePort,
                                               RulePackageExecutor dryRunRulePackageExecutor,
                                               ObjectMapper objectMapper) {
        return new DryRunAsyncRunner(jobRepository, targetPort, sampleSourcePort,
                dryRunRulePackageExecutor, objectMapper);
    }

    @Bean
    public DryRunService dryRunService(DryRunJobRepository jobRepository,
                                       DryRunAsyncRunner dryRunAsyncRunner) {
        return new DryRunService(jobRepository, dryRunAsyncRunner);
    }
}
