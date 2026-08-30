package com.riskplatform.gateway.infrastructure.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.riskplatform.common.crypto.EncryptedStringTypeHandler;

import java.time.LocalDateTime;

/**
 * 事中订单持久化对象（对应 risk_order 表，R10）。
 *
 * <p>{@code context} 为事件上下文，含交易主体名称/证件号等敏感数据，落库时经
 * {@link EncryptedStringTypeHandler} 透明加密为 AES-256-GCM 密文，读取时自动解密（R17.4）。
 * 因使用自定义 TypeHandler，实体声明 {@code autoResultMap = true}。
 */
@TableName(value = "risk_order", autoResultMap = true)
public class RiskOrderPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String businessOrderId;
    private String merchantId;
    private String eventTypeCode;
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String context;
    private String finalDecision;
    private LocalDateTime eventTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getBusinessOrderId() {
        return businessOrderId;
    }

    public void setBusinessOrderId(String businessOrderId) {
        this.businessOrderId = businessOrderId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public void setEventTypeCode(String eventTypeCode) {
        this.eventTypeCode = eventTypeCode;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getFinalDecision() {
        return finalDecision;
    }

    public void setFinalDecision(String finalDecision) {
        this.finalDecision = finalDecision;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }
}
