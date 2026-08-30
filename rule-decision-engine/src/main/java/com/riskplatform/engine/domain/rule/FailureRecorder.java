package com.riskplatform.engine.domain.rule;

/**
 * 规则失败恢复处理（R5.3/R5.4）。
 *
 * <p>当规则求值异常时调用，用于标记失败/记录原因。若该恢复处理本身抛出异常，
 * 规则执行器将其视为致命错误（R5.4），停止剩余规则。
 */
@FunctionalInterface
public interface FailureRecorder {

    /**
     * 记录某规则的失败。
     *
     * @throws RuntimeException 若恢复处理本身失败，触发致命错误路径
     */
    void recordFailure(ExecutableRule rule, Throwable cause);
}
