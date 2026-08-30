package com.riskplatform.ruleconfig.domain.rule;

import java.util.Set;

/**
 * 表达式校验端口（R3.2/R3.5/R3.6）。
 *
 * <p>由基础设施层用 Aviator 实现：编译表达式（返回语法错误位置/描述）、
 * 提取表达式引用的变量并校验是否均已声明（指标引用名 + 事件上下文字段）。
 */
public interface ExpressionValidator {

    /**
     * 校验表达式。
     *
     * @param expression     规则/累计脚本表达式
     * @param declaredFields 已声明的可引用字段集合
     * @return 校验结果（语法错误或未声明字段）
     */
    ExpressionValidationResult validate(String expression, Set<String> declaredFields);
}
