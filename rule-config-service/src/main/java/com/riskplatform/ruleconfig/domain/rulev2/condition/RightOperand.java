package com.riskplatform.ruleconfig.domain.rulev2.condition;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 叶子条件右值（R2.3）。
 *
 * <p>对齐 design.md condition_json 中叶子节点的 {@code right} 结构，按 {@link #kind} 取不同字段：
 * <ul>
 *   <li>{@code CONST}      → {@link #value}（常量）</li>
 *   <li>{@code VARIABLE}   → {@link #variable}（另一变量）</li>
 *   <li>{@code ENUM_LIB}   → {@link #enumLibCode}（枚举库编码）</li>
 *   <li>{@code IMPORT_SET} → {@link #importValues}（批量导入值集合）</li>
 * </ul>
 *
 * @param kind         右值取值形式
 * @param value        常量值（kind=CONST）
 * @param variable     变量引用（kind=VARIABLE）
 * @param enumLibCode  枚举库编码（kind=ENUM_LIB）
 * @param importValues 批量导入值集合（kind=IMPORT_SET）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RightOperand(RightOperandKind kind,
                           Object value,
                           Variable variable,
                           String enumLibCode,
                           List<String> importValues) {

    /** 常量右值工厂。 */
    public static RightOperand ofConst(Object value) {
        return new RightOperand(RightOperandKind.CONST, value, null, null, null);
    }

    /** 变量右值工厂。 */
    public static RightOperand ofVariable(Variable variable) {
        return new RightOperand(RightOperandKind.VARIABLE, null, variable, null, null);
    }

    /** 枚举库右值工厂。 */
    public static RightOperand ofEnumLib(String enumLibCode) {
        return new RightOperand(RightOperandKind.ENUM_LIB, null, null, enumLibCode, null);
    }

    /** 批量导入右值工厂。 */
    public static RightOperand ofImportSet(List<String> importValues) {
        return new RightOperand(RightOperandKind.IMPORT_SET, null, null, null, importValues);
    }
}
