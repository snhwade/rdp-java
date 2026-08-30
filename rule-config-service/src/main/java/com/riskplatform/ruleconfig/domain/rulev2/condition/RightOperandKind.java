package com.riskplatform.ruleconfig.domain.rulev2.condition;

/**
 * 右值取值形式（R2.3）。
 */
public enum RightOperandKind {
    /** 常量值。 */
    CONST,
    /** 另一变量（字段/指标/模型/赋值字段）。 */
    VARIABLE,
    /** 枚举库引用（按 enumLibCode 取枚举值集合）。 */
    ENUM_LIB,
    /** 批量导入的值集合。 */
    IMPORT_SET
}
