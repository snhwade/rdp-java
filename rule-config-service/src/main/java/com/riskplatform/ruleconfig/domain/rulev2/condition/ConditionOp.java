package com.riskplatform.ruleconfig.domain.rulev2.condition;

/**
 * 条件树节点操作类型（R2.4）。
 *
 * <p>分支节点用 AND/OR/NOT 组合子节点，叶子节点用 LEAF 表示一个
 * 「左变量 + 运算符 + 右值」的比较条件。
 */
public enum ConditionOp {
    /** 逻辑与：所有子节点都满足（对应 Aviator {@code &&}）。 */
    AND,
    /** 逻辑或：任一子节点满足（对应 Aviator {@code ||}）。 */
    OR,
    /** 逻辑非：唯一子节点取反（对应 Aviator {@code !}）。 */
    NOT,
    /** 叶子比较节点：left operator right。 */
    LEAF
}
