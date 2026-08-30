package com.riskplatform.gateway.infrastructure.decisionlog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_decision_record")
public class AiDecisionRecordPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String correlationId;
    private String businessOrderId;
    private String merchantId;
    private String eventTypeCode;
    private LocalDateTime eventTime;
    private String status;
    private String agentDecision;
    private Double confidence;
    private String reason;
    private String engineDecision;
    private Boolean divergence;
    private String traceJson;
    private String failReason;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

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

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
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

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAgentDecision() {
        return agentDecision;
    }

    public void setAgentDecision(String agentDecision) {
        this.agentDecision = agentDecision;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEngineDecision() {
        return engineDecision;
    }

    public void setEngineDecision(String engineDecision) {
        this.engineDecision = engineDecision;
    }

    public Boolean getDivergence() {
        return divergence;
    }

    public void setDivergence(Boolean divergence) {
        this.divergence = divergence;
    }

    public String getTraceJson() {
        return traceJson;
    }

    public void setTraceJson(String traceJson) {
        this.traceJson = traceJson;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
