package com.riskplatform.ruleconfig.domain.rulev2.condition;

/**
 * 左变量数据类型（R2.1）。
 *
 * <p>用于「运算符与数据类型适配校验」（R2.2）：不同数据类型仅允许使用其适配的运算符集合。
 */
public enum DataType {
    /** 数值型（整数/小数）。 */
    NUMBER,
    /** 字符串型。 */
    STRING,
    /** 布尔型。 */
    BOOLEAN,
    /** 集合型（多值）。 */
    COLLECTION,
    /** 日期型。 */
    DATE
}
