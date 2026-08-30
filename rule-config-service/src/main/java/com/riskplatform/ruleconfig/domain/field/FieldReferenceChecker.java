package com.riskplatform.ruleconfig.domain.field;

import java.util.List;

/**
 * 全局字段库引用检查端口（参数管理优化 Q1-B）。
 *
 * <p>删除字段或修改字段 code 前，检查是否仍被事件字段绑定、规则、决策流、指标引用；
 * 存在引用则阻断，并返回引用类型描述列表。
 */
public interface FieldReferenceChecker {

    /**
     * @param fieldId   字段主键
     * @param fieldCode 字段 code（用于 JSON/表达式包含匹配）
     * @return 引用类型描述；空列表表示可安全删除或改 code
     */
    List<String> findReferences(Long fieldId, String fieldCode);

    static FieldReferenceChecker noop() {
        return (fieldId, fieldCode) -> List.of();
    }
}
