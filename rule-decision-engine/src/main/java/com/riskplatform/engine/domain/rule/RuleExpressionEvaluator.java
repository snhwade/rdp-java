package com.riskplatform.engine.domain.rule;

import java.util.Map;

/**
 * 规则表达式求值端口（由基础设施层用 Aviator 实现）。
 *
 * <p>入参为事件上下文与被引用指标当前值的合并视图；返回该规则是否命中。
 * 求值异常应向上抛出，由规则执行器按 R5.3/R5.4 处理。
 */
@FunctionalInterface
public interface RuleExpressionEvaluator {

    boolean evaluate(String expression, Map<String, Object> context);
}
