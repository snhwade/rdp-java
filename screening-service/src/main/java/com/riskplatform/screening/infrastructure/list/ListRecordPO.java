package com.riskplatform.screening.infrastructure.list;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.riskplatform.common.crypto.EncryptedStringTypeHandler;

import java.time.LocalDateTime;

/**
 * 名单记录持久化对象（对应 list_record 表，S1）。
 *
 * <p>{@code dimensionValue} 为名单条目维度值，含交易主体名称/证件号等敏感数据，落库时经
 * {@link EncryptedStringTypeHandler} 透明加密为 AES-256-GCM 密文，读取时自动解密（R17.4）。
 * 因使用自定义 TypeHandler，实体声明 {@code autoResultMap = true}。
 *
 * <p>注意：{@code dimensionValue} 采用随机 IV 加密（非确定性），密文不可用于 SQL 等值匹配；
 * 按值查询改由仓储在解密后于内存中过滤（见 {@code ListRecordRepositoryImpl}）。
 */
@TableName(value = "list_record", autoResultMap = true)
public class ListRecordPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String listType;
    private String dimension;
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String dimensionValue;
    private String reason;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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
