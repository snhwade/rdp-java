package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.riskplatform.common.crypto.EncryptedStringTypeHandler;

import java.time.LocalDateTime;

/**
 * 历史订单样本只读持久化对象（对应 risk_order 表，R5.1/R5.2）。
 *
 * <p>试运行从该表读取历史订单作为影子样本。{@code context} 为事件上下文，落库时由
 * decision-gateway 经 {@link EncryptedStringTypeHandler} 透明加密（AES-256-GCM，R17.4），
 * 本侧读取时自动解密为明文（需装配 {@code FieldCryptoConfig}）。因使用自定义 TypeHandler，
 * 实体声明 {@code autoResultMap = true}。
 *
 * <p>仅用于试运行只读取样，不修改 risk_order；映射字段与 gateway 侧 RiskOrderPO 对齐。
 */
@TableName(value = "risk_order", autoResultMap = true)
public class RiskOrderSamplePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
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
