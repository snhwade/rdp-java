package com.riskplatform.gateway.infrastructure.standalone;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.riskplatform.common.crypto.EncryptedStringTypeHandler;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@TableName(value = "list_record", autoResultMap = true)
public class StandaloneListRecordPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String listType;
    private String dimension;
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String dimensionValue;
    private Long immuneRuleId;
    private LocalDateTime expireAt;
    private Integer enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getListType() {
        return listType;
    }

    public void setListType(String listType) {
        this.listType = listType;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getDimensionValue() {
        return dimensionValue;
    }

    public void setDimensionValue(String dimensionValue) {
        this.dimensionValue = dimensionValue;
    }

    public Long getImmuneRuleId() {
        return immuneRuleId;
    }

    public void setImmuneRuleId(Long immuneRuleId) {
        this.immuneRuleId = immuneRuleId;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
