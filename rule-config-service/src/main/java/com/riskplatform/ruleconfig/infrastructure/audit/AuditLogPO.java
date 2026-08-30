package com.riskplatform.ruleconfig.infrastructure.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * audit_log 表持久化对象（R17.3）。
 *
 * <p>表无 created_at/updated_at/created_by 等通用审计列，故不参与自动填充；
 * op_time 由切面写入。op_content 为 JSON 列，以 JSON 字符串落库。
 */
@TableName("audit_log")
public class AuditLogPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String operator;
    private LocalDateTime opTime;
    private String opType;
    private String targetType;
    private String opContent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public LocalDateTime getOpTime() {
        return opTime;
    }

    public void setOpTime(LocalDateTime opTime) {
        this.opTime = opTime;
    }

    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getOpContent() {
        return opContent;
    }

    public void setOpContent(String opContent) {
        this.opContent = opContent;
    }
}
