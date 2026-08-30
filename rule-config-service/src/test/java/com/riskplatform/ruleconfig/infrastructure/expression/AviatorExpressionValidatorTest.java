package com.riskplatform.ruleconfig.infrastructure.expression;

import com.riskplatform.ruleconfig.domain.rule.ExpressionValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aviator 表达式校验单元测试（R3.2/R3.5/R3.6）。
 */
class AviatorExpressionValidatorTest {

    private final AviatorExpressionValidator validator = new AviatorExpressionValidator();

    @Test
    void validExpression_withDeclaredFields_passes() {
        ExpressionValidationResult r = validator.validate(
                "amount > 100 && riskLevel == 'HIGH'",
                Set.of("amount", "riskLevel"));
        assertThat(r.valid()).isTrue();
    }

    @Test
    void syntaxError_reported() {
        ExpressionValidationResult r = validator.validate("amount > 100 && (", Set.of("amount"));
        assertThat(r.valid()).isFalse();
        assertThat(r.syntaxError()).isNotBlank();
    }

    @Test
    void undeclaredField_reported() {
        ExpressionValidationResult r = validator.validate(
                "amount > 100 && unknownField == 1",
                Set.of("amount"));
        assertThat(r.valid()).isFalse();
        assertThat(r.undeclaredFields()).contains("unknownField");
    }

    @Test
    void nestedField_rootDeclared_passes() {
        ExpressionValidationResult r = validator.validate(
                "txnCount7d > 5",
                Set.of("txnCount7d"));
        assertThat(r.valid()).isTrue();
    }
}
