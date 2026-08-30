package com.riskplatform.ruleconfig.infrastructure.enums;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * enum_value 表持久化对象（R12.2）。
 */
@TableName("enum_value")
public class EnumValuePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enumLibId;
    private String value;
    private String label;
    private Integer orderNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEnumLibId() {
        return enumLibId;
    }

    public void setEnumLibId(Long enumLibId) {
        this.enumLibId = enumLibId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(Integer orderNo) {
        this.orderNo = orderNo;
    }
}
