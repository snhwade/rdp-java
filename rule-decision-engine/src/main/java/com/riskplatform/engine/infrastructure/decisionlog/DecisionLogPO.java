package com.riskplatform.engine.infrastructure.decisionlog;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 决策日志持久化对象（对应 decision_log 表，R6.6/R15.1）。
 * hit_rules 以 JSON 文本存储命中规则及各自决策与优先级。
 */
@TableName("decision_log")
public class DecisionLogPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String finalDecision;
    private String hitRules;
    private Integer elapsedMs;
    private String timeoutReason;
    private String groupStatus;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

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

    public String getFinalDecision() {
        return finalDecision;
    }

    public void setFinalDecision(String finalDecision) {
        this.finalDecision = finalDecision;
    }

    public String getHitRules() {
        return hitRules;
    }

    public void setHitRules(String hitRules) {
        this.hitRules = hitRules;
    }

    public Integer getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Integer elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getTimeoutReason() {
        return timeoutReason;
    }

    public void setTimeoutReason(String timeoutReason) {
        this.timeoutReason = timeoutReason;
    }

    public String getGroupStatus() {
        return groupStatus;
    }

    public void setGroupStatus(String groupStatus) {
        this.groupStatus = groupStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
