package com.riskplatform.ruleconfig.infrastructure.enums;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * enum_lib 表持久化对象（R12.2）。审计列由数据库维护，不映射。
 */
@TableName("enum_lib")
public class EnumLibPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    /** STRING/LONG/DOUBLE/BOOLEAN */
    private String dataType;
    /** ENABLED/DISABLED */
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
