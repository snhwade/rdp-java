package com.riskplatform.ruleconfig.domain.rule;

import java.util.List;

/**
 * 规则/累计脚本表达式校验结果（R3.2/R3.6）。
 *
 * @param valid             是否通过校验
 * @param syntaxError       语法错误描述（含位置；无则 null）
 * @param undeclaredFields  引用的未声明字段（无则空列表）
 */
public record ExpressionValidationResult(boolean valid, String syntaxError, List<String> undeclaredFields) {

    public static ExpressionValidationResult ok() {
        return new ExpressionValidationResult(true, null, List.of());
    }

    public static ExpressionValidationResult syntaxError(String message) {
        return new ExpressionValidationResult(false, message, List.of());
    }

    public static ExpressionValidationResult undeclared(List<String> fields) {
        return new ExpressionValidationResult(false, null, fields);
    }
}
