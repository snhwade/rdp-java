package com.riskplatform.ruleconfig.infrastructure.dict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * decision_tag 表持久化对象（R12.1）。审计列由数据库维护，不映射。
 */
@TableName("decision_tag")
public class DecisionTagPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String applicableAssetType;
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

    public String getApplicableAssetType() {
        return applicableAssetType;
    }

    public void setApplicableAssetType(String applicableAssetType) {
        this.applicableAssetType = applicableAssetType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
