package com.riskplatform.engine.domain.rule;

import com.riskplatform.engine.domain.decision.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 规则执行单元测试（R5.1–R5.6，含异常/致命路径）。
 */
class RuleExecutorTest {

    private RuleExecutor executor(RuleExpressionEvaluator eval, FailureRecorder recorder) {
        return new RuleExecutor(eval, recorder);
    }

    private static final FailureRecorder NOOP = (rule, cause) -> { };

    @Test
    void executesInPriorityThenIdOrder() {
        // 评估器：命中 ruleId 偶数
        RuleExecutor ex = executor((expr, ctx) -> expr.equals("hit"), NOOP);
        List<ExecutableRule> rules = List.of(
                new ExecutableRule(2L, 1, 50, "miss", Decision.PASS, false),
                new ExecutableRule(1L, 1, 100, "hit", Decision.REVIEW, false));
        RuleExecutionResult r = ex.execute(rules, Map.of());
        // 优先级 100 的 rule1 先执行
        assertThat(r.records().get(0).ruleId()).isEqualTo(1L);
        assertThat(r.status()).isEqualTo(GroupExecutionStatus.COMPLETED);
    }

    @Test
    void shortCircuit_stopsLowerPriorityRules() {
        RuleExecutor ex = executor((expr, ctx) -> true, NOOP);
        List<ExecutableRule> rules = List.of(
                new ExecutableRule(1L, 1, 10, "e", Decision.PASS, false),
                new ExecutableRule(2L, 1, 100, "e", Decision.REJECT, true));
        RuleExecutionResult r = ex.execute(rules, Map.of());
        // 高优先级短路后只执行了 rule2
        assertThat(r.records()).hasSize(1);
        assertThat(r.hitDecisions()).hasSize(1);
        assertThat(r.hitDecisions().get(0).ruleId()).isEqualTo(2L);
    }

    @Test
    void evalException_isolatedAndContinues() {
        RuleExpressionEvaluator eval = (expr, ctx) -> {
            if (expr.equals("boom")) {
                throw new RuntimeException("eval failed");
            }
            return true;
        };
        RuleExecutor ex = executor(eval, NOOP);
        List<ExecutableRule> rules = List.of(
                new ExecutableRule(1L, 1, 10, "boom", Decision.REJECT, false),
                new ExecutableRule(2L, 1, 100, "ok", Decision.REVIEW, false));
        RuleExecutionResult r = ex.execute(rules, Map.of());
        assertThat(r.status()).isEqualTo(GroupExecutionStatus.COMPLETED);
        // 失败规则不贡献决策，但后续规则继续执行并命中
        assertThat(r.hitDecisions()).hasSize(1);
        assertThat(r.hitDecisions().get(0).ruleId()).isEqualTo(2L);
        assertThat(r.records().stream().filter(RuleExecutionRecord::failed).findFirst().orElseThrow().ruleId())
                .isEqualTo(1L);
    }

    @Test
    void fatalRecoveryFailure_interruptsAndPreservesHits() {
        RuleExpressionEvaluator eval = (expr, ctx) -> {
            if (expr.equals("boom")) {
                throw new RuntimeException("eval failed");
            }
            return true;
        };
        FailureRecorder fatal = (rule, cause) -> {
            throw new RuntimeException("recovery failed");
        };
        RuleExecutor ex = executor(eval, fatal);
        List<ExecutableRule> rules = List.of(
                new ExecutableRule(1L, 1, 100, "ok", Decision.REVIEW, false),   // 先命中
                new ExecutableRule(2L, 1, 50, "boom", Decision.REJECT, false),   // 触发致命
                new ExecutableRule(3L, 1, 10, "ok", Decision.REJECT, false));    // 不应执行
        RuleExecutionResult r = ex.execute(rules, Map.of());
        assertThat(r.status()).isEqualTo(GroupExecutionStatus.INTERRUPTED);
        assertThat(r.fatalReason()).isEqualTo("recovery failed");
        // 已有命中（rule1）被保留
        assertThat(r.hitDecisions()).hasSize(1);
        assertThat(r.hitDecisions().get(0).ruleId()).isEqualTo(1L);
        // rule3 未执行
        assertThat(r.records().stream().anyMatch(rec -> rec.ruleId() == 3L)).isFalse();
    }

    @Test
    void recordsEveryExecution() {
        RuleExecutor ex = executor((expr, ctx) -> expr.equals("hit"), NOOP);
        List<ExecutableRule> rules = List.of(
                new ExecutableRule(1L, 2, 100, "hit", Decision.REVIEW, false),
                new ExecutableRule(2L, 3, 10, "miss", Decision.PASS, false));
        RuleExecutionResult r = ex.execute(rules, Map.of());
        assertThat(r.records()).hasSize(2);
        assertThat(r.records().get(0).version()).isEqualTo(2);
    }
}
