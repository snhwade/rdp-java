package com.riskplatform.ruleconfig.infrastructure.dict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * risk_type 表持久化对象（R12.1）。
 *
 * <p>审计列 create_time/update_time/create_user/update_user 由数据库默认值与触发器维护，此处不映射。
 */
@TableName("risk_type")
public class RiskTypePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
