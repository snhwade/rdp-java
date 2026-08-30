package com.riskplatform.engine.domain.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 规则执行算法（R5.1–R5.6）。
 *
 * <p>执行流程：
 * <ol>
 *   <li>按 (priority desc, ruleId asc) 确定顺序依次执行启用规则（R5.1；数值越大优先级越高）；</li>
 *   <li>基于事件上下文与指标当前值求值（R5.2）；命中则产出决策并记录；</li>
 *   <li>命中短路规则后停止更低优先级（数值更小）规则（R5.6）；</li>
 *   <li>求值异常：标记失败、记录原因、计未命中且不贡献决策，继续其余规则（R5.3）；</li>
 *   <li>失败恢复处理本身异常视为致命错误：停止剩余规则、保留已有命中、状态置 INTERRUPTED（R5.4）。</li>
 * </ol>
 */
public class RuleExecutor {

    private final RuleExpressionEvaluator evaluator;
    private final FailureRecorder failureRecorder;

    public RuleExecutor(RuleExpressionEvaluator evaluator, FailureRecorder failureRecorder) {
        this.evaluator = evaluator;
        this.failureRecorder = failureRecorder;
    }

    /**
     * 执行一组规则。
     *
     * @param rules      规则组下启用的规则
     * @param context    事件上下文 + 指标当前值的合并视图
     * @return 执行结果（命中决策、执行记录、组状态）
     */
    public RuleExecutionResult execute(List<ExecutableRule> rules, Map<String, Object> context) {
        List<ExecutableRule> ordered = rules.stream()
                .sorted(Comparator.comparingInt(ExecutableRule::priority).reversed()
                        .thenComparingLong(ExecutableRule::ruleId))
                .toList();

        List<HitDecision> hits = new ArrayList<>();
        List<RuleExecutionRecord> records = new ArrayList<>();

        for (ExecutableRule rule : ordered) {
            if (isImmune(context, rule.ruleId())) {
                records.add(RuleExecutionRecord.miss(rule.ruleId(), rule.version()));
                continue;
            }
            boolean hit;
            try {
                hit = evaluator.evaluate(rule.expression(), context); // R5.2
            } catch (RuntimeException evalEx) {
                // R5.3：求值异常 -> 标记失败、记录原因、计未命中、不贡献决策、继续
                try {
                    failureRecorder.recordFailure(rule, evalEx);
                } catch (RuntimeException recoveryEx) {
                    // R5.4：恢复处理本身异常 -> 致命错误，停止剩余规则，保留已有命中
                    records.add(RuleExecutionRecord.failed(rule.ruleId(), rule.version(),
                            "fatal recovery failure: " + recoveryEx.getMessage()));
                    return new RuleExecutionResult(List.copyOf(hits), List.copyOf(records),
                            GroupExecutionStatus.INTERRUPTED, recoveryEx.getMessage());
                }
                records.add(RuleExecutionRecord.failed(rule.ruleId(), rule.version(), evalEx.getMessage()));
                continue;
            }

            if (hit) {
                records.add(RuleExecutionRecord.hit(rule.ruleId(), rule.version()));
                hits.add(new HitDecision(rule.ruleId(), rule.priority(), rule.decision(), rule.trialRun()));
                if (rule.shortCircuited()) { // R5.6
                    break;
                }
            } else {
                records.add(RuleExecutionRecord.miss(rule.ruleId(), rule.version()));
            }
        }

        return new RuleExecutionResult(List.copyOf(hits), List.copyOf(records),
                GroupExecutionStatus.COMPLETED, null);
    }

    /** 白名单免疫（S1）：whiteImmuneAll 或 immuneRuleIds 包含当前规则时跳过执行。 */
    @SuppressWarnings("unchecked")
    private static boolean isImmune(Map<String, Object> context, long ruleId) {
        if (context == null || !Boolean.TRUE.equals(context.get("whiteHit"))) {
            return false;
        }
        if (Boolean.TRUE.equals(context.get("whiteImmuneAll"))) {
            return true;
        }
        Object ids = context.get("immuneRuleIds");
        if (ids instanceof Iterable<?> iterable) {
            for (Object id : iterable) {
                if (id instanceof Number n && n.longValue() == ruleId) {
                    return true;
                }
                if (id != null) {
                    try {
                        if (Long.parseLong(String.valueOf(id)) == ruleId) {
                            return true;
                        }
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                }
            }
        }
        return false;
    }
}
