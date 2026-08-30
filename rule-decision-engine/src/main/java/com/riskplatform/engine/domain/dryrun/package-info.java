/**
 * 试运行（影子模式）领域模型（R5.1–R5.6）。
 *
 * <p>本子域承载「用历史样本对规则/规则包空跑评估命中率」的影子模式核心模型：
 * <ul>
 *   <li>{@link com.riskplatform.engine.domain.dryrun.DryRunJob} 试运行任务聚合（状态流转 + 统计结果）；</li>
 *   <li>{@link com.riskplatform.engine.domain.dryrun.DryRunReport} 试运行报告（命中分布/评分分布/明细摘要）；</li>
 *   <li>{@link com.riskplatform.engine.domain.dryrun.DryRunSample} 影子上下文样本；</li>
 *   <li>端口：{@link com.riskplatform.engine.domain.dryrun.DryRunSampleSourcePort} 样本来源、
 *       {@link com.riskplatform.engine.domain.dryrun.DryRunTargetPort} 目标定义加载、
 *       {@link com.riskplatform.engine.domain.dryrun.DryRunJobRepository} 任务仓储。</li>
 * </ul>
 *
 * <p><b>严格隔离（R5.2/R5.6）</b>：试运行复用 {@code RulePackageExecutor} 逐条评估，但
 * <em>不写 decision_log、不输出真实策略（不写 decision_strategy_output）、不参与任何指标累计与告警</em>。
 * 评估结果仅用于统计落 {@code dry_run_job}。隔离由编排服务（application 层 DryRunService）保证：
 * 其依赖项中不含 DecisionLogService / DecisionStrategyOutputService / DecisionMetrics。
 */
package com.riskplatform.engine.domain.dryrun;
