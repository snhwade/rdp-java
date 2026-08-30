package com.riskplatform.ruleconfig.domain.field;

import java.util.List;

/**
 * 字段关联关系视图（R3.7）：引用某全局字段的事件、枚举值与衍生字段，以及血缘阻断引用类型。
 *
 * @param fieldId    字段主键
 * @param fieldCode  字段英文标识
 * @param fieldName  字段名称
 * @param events     引用该字段的事件类型 code 列表
 * @param enumValues 引用该字段的枚举值列表
 * @param derivedFields 引用该字段的衍生字段列表
 * @param blockingReferences 会阻断删除/改 code 的引用类型（事件字段/规则包/决策流/指标）
 */
public record FieldRelations(Long fieldId,
                             String fieldCode,
                             String fieldName,
                             List<String> events,
                             List<EnumValueRef> enumValues,
                             List<DerivedField> derivedFields,
                             List<String> blockingReferences) {

    /**
     * 枚举值引用条目。
     *
     * @param enumLibId 枚举库ID
     * @param value     枚举值
     * @param label     显示标签
     */
    public record EnumValueRef(Long enumLibId, String value, String label) {
    }
}
