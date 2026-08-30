package com.riskplatform.ruleconfig.domain.field;

import com.riskplatform.common.error.ValidationException;

/**
 * 衍生字段（S7）：基于已有上下文字段用 Aviator 表达式计算出的新字段。
 *
 * @param id            主键
 * @param eventTypeCode 关联事件类型
 * @param name          衍生字段名
 * @param expression    Aviator 表达式（引用已有上下文字段）
 * @param enabled       是否启用
 */
public record DerivedField(Long id, String eventTypeCode, String name, String expression, boolean enabled) {

    public static DerivedField create(String eventTypeCode, String name, String expression) {
        ValidationException.Builder errors = ValidationException.builder();
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            errors.field("eventTypeCode", "必填");
        }
        if (name == null || name.isBlank()) {
            errors.field("name", "必填");
        }
        if (expression == null || expression.isBlank()) {
            errors.field("expression", "必填");
        }
        errors.throwIfAny();
        return new DerivedField(null, eventTypeCode, name, expression, true);
    }
}
