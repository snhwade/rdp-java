package com.riskplatform.ruleconfig.domain.rulev2.condition;

import java.util.EnumSet;
import java.util.Set;

/**
 * 叶子条件运算符（R2.2）。
 *
 * <p>每个运算符声明其适配的左变量数据类型集合，供「运算符与数据类型适配校验」使用。
 * 数据类型与运算符不匹配时由 {@link Variable}/{@link ConditionNode} 校验抛出
 * {@link com.riskplatform.common.error.ValidationException}。
 *
 * <p>适配关系（与 R2.2 对齐）：
 * <ul>
 *   <li>数值/日期：{@code GT GTE LT LTE EQ NEQ}</li>
 *   <li>字符串：{@code EQ NEQ CONTAINS STARTS_WITH IN NOT_IN}</li>
 *   <li>集合：{@code IN NOT_IN CONTAINS}</li>
 *   <li>布尔：{@code EQ NEQ}</li>
 * </ul>
 */
public enum Operator {
    /** 大于。 */
    GT(EnumSet.of(DataType.NUMBER, DataType.DATE)),
    /** 大于等于。 */
    GTE(EnumSet.of(DataType.NUMBER, DataType.DATE)),
    /** 小于。 */
    LT(EnumSet.of(DataType.NUMBER, DataType.DATE)),
    /** 小于等于。 */
    LTE(EnumSet.of(DataType.NUMBER, DataType.DATE)),
    /** 等于。 */
    EQ(EnumSet.of(DataType.NUMBER, DataType.DATE, DataType.STRING, DataType.BOOLEAN)),
    /** 不等于。 */
    NEQ(EnumSet.of(DataType.NUMBER, DataType.DATE, DataType.STRING, DataType.BOOLEAN)),
    /** 包含（字符串子串 / 集合包含元素）。 */
    CONTAINS(EnumSet.of(DataType.STRING, DataType.COLLECTION)),
    /** 以……开头（字符串前缀）。 */
    STARTS_WITH(EnumSet.of(DataType.STRING)),
    /** 属于（左值在右值集合内）。 */
    IN(EnumSet.of(DataType.STRING, DataType.COLLECTION, DataType.NUMBER)),
    /** 不属于（左值不在右值集合内）。 */
    NOT_IN(EnumSet.of(DataType.STRING, DataType.COLLECTION, DataType.NUMBER));

    private final Set<DataType> supportedTypes;

    Operator(Set<DataType> supportedTypes) {
        this.supportedTypes = supportedTypes;
    }

    /**
     * 判断该运算符是否适配给定的左变量数据类型。
     *
     * @param dataType 左变量数据类型
     * @return 适配返回 {@code true}
     */
    public boolean supports(DataType dataType) {
        return dataType != null && supportedTypes.contains(dataType);
    }
}
