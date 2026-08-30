package com.riskplatform.screening.infrastructure.listmgmt;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("list_attr_def")
public class ListAttrDefPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String inputType;
    private Integer required;
    private Integer multiValue;
    private String maskRule;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInputType() { return inputType; }
    public void setInputType(String inputType) { this.inputType = inputType; }
    public Integer getRequired() { return required; }
    public void setRequired(Integer required) { this.required = required; }
    public Integer getMultiValue() { return multiValue; }
    public void setMultiValue(Integer multiValue) { this.multiValue = multiValue; }
    public String getMaskRule() { return maskRule; }
    public void setMaskRule(String maskRule) { this.maskRule = maskRule; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
