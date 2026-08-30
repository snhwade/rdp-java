package com.riskplatform.ruleconfig.domain.audit;

import java.time.LocalDateTime;

/**
 * 审计日志记录（R17.3）。
 *
 * <p>记录对事件类型/规则/规则组/指标定义的创建、更新、删除操作，
 * 含操作人（{@code operator}）、操作时间（{@code opTime}）与操作内容（{@code opContent}，JSON 串）。
 */
public class AuditLog {

    private Long id;
    private final String operator;
    private final LocalDateTime opTime;
    private final AuditOpType opType;
    private final AuditTargetType targetType;
    private final String opContent;

    public AuditLog(String operator, LocalDateTime opTime, AuditOpType opType,
                    AuditTargetType targetType, String opContent) {
        this.operator = operator;
        this.opTime = opTime;
        this.opType = opType;
        this.targetType = targetType;
        this.opContent = opContent;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOperator() {
        return operator;
    }

    public LocalDateTime getOpTime() {
        return opTime;
    }

    public AuditOpType getOpType() {
        return opType;
    }

    public AuditTargetType getTargetType() {
        return targetType;
    }

    public String getOpContent() {
        return opContent;
    }
}
