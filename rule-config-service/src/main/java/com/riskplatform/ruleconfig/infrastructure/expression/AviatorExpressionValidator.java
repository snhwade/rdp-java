package com.riskplatform.ruleconfig.infrastructure.expression;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.riskplatform.ruleconfig.domain.rule.ExpressionValidationResult;
import com.riskplatform.ruleconfig.domain.rule.ExpressionValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 基于 Aviator 的表达式校验实现（R3.2/R3.5/R3.6）。
 *
 * <p>编译失败 → 语法错误（含异常信息作为描述）；编译成功 → 检查表达式引用的变量
 * 是否均在已声明字段集合内，存在未声明字段则返回其名称。
 */
@Component
public class AviatorExpressionValidator implements ExpressionValidator {

    private final AviatorEvaluatorInstance instance = AviatorEvaluator.newInstance();

    @Override
    public ExpressionValidationResult validate(String expression, Set<String> declaredFields) {
        Expression compiled;
        try {
            compiled = instance.compile(expression, true);
        } catch (Exception e) {
            return ExpressionValidationResult.syntaxError("表达式语法错误: " + e.getMessage());
        }
        // 提取引用变量，校验是否均已声明
        List<String> undeclared = new ArrayList<>();
        for (String var : compiled.getVariableNames()) {
            String root = var.contains(".") ? var.substring(0, var.indexOf('.')) : var;
            if (!declaredFields.contains(var) && !declaredFields.contains(root)) {
                undeclared.add(var);
            }
        }
        if (!undeclared.isEmpty()) {
            return ExpressionValidationResult.undeclared(undeclared);
        }
        return ExpressionValidationResult.ok();
    }
}
