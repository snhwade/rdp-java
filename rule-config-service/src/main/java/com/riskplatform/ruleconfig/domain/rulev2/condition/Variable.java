package com.riskplatform.ruleconfig.domain.rulev2.condition;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 条件树左变量 / 右值变量（R2.1）。
 *
 * <p>对齐 design.md condition_json 中 {@code left} 与右值 {@code variable} 结构：
 * <pre>{ "source": "INDICATOR", "ref": "txn_amt_3d", "dataType": "NUMBER" }</pre>
 *
 * @param source   变量来源（FIELD/INDICATOR/MODEL/ASSIGNMENT）
 * @param ref      引用标识名（字段名/指标注入名/模型键/赋值字段键）
 * @param dataType 数据类型，决定可适配的运算符集合（R2.2）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Variable(VariableSource source, String ref, DataType dataType) {
}
