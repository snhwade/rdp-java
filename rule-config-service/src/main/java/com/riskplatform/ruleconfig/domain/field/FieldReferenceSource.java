package com.riskplatform.ruleconfig.domain.field;

/**
 * 全局字段引用来源（事件字段 / 规则 / 决策流 / 指标等）。
 */
public interface FieldReferenceSource {

    /** 引用类型展示名，如「事件字段」「规则包」。 */
    String referenceType();

    boolean isReferenced(Long fieldId, String fieldCode);
}
