package com.riskplatform.ruleconfig.domain.enums;

import com.riskplatform.common.error.ValidationException;

/**
 * 枚举值实体（R12.2）。隶属于某枚举库（enumLibId）。
 *
 * <p>不变式：value 长度 1..256；label 可空，长度 <=256。同一枚举库内 value 唯一（由应用层校验）。
 */
public class EnumValue {

    public static final int VALUE_MAX = 256;
    public static final int LABEL_MAX = 256;

    private Long id;
    private Long enumLibId;
    private String value;
    private String label;
    private int orderNo;

    private EnumValue() {
    }

    /** 工厂方法：创建枚举值并校验。 */
    public static EnumValue create(Long enumLibId, String value, String label, int orderNo) {
        EnumValue v = new EnumValue();
        v.enumLibId = enumLibId;
        v.value = value;
        v.label = label;
        v.orderNo = orderNo;
        v.validate();
        return v;
    }

    /** 从持久化重建（不重复校验）。 */
    public static EnumValue rehydrate(Long id, Long enumLibId, String value, String label, int orderNo) {
        EnumValue v = new EnumValue();
        v.id = id;
        v.enumLibId = enumLibId;
        v.value = value;
        v.label = label;
        v.orderNo = orderNo;
        return v;
    }

    /** 校验不变式。 */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (value == null || value.isEmpty()) {
            errors.field("value", "必填");
        } else if (value.length() > VALUE_MAX) {
            errors.field("value", "长度不能超过 " + VALUE_MAX + " 个字符");
        }
        if (label != null && label.length() > LABEL_MAX) {
            errors.field("label", "长度不能超过 " + LABEL_MAX + " 个字符");
        }
        errors.throwIfAny();
    }

    public Long getId() {
        return id;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public Long getEnumLibId() {
        return enumLibId;
    }

    public void assignEnumLibId(Long enumLibId) {
        this.enumLibId = enumLibId;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public int getOrderNo() {
        return orderNo;
    }
}
