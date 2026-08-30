package com.riskplatform.ruleconfig.domain.approval;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.ValidationException;

import java.time.LocalDateTime;

/**
 * 复核审批申请聚合根（S5，Maker-Checker）。
 *
 * <p>配置变更先以草稿提交进入 PENDING，审批人通过(APPROVED)后才生效，或驳回(REJECTED)。
 * 状态机：PENDING → APPROVED / REJECTED；已审结不可再变更。
 *
 * <p>不变式：assetType/opType/targetId/applicant 必填；payload 对 CREATE/UPDATE 必填。
 */
public class ApprovalRequest {

    public enum Status { PENDING, APPROVED, REJECTED }

    private Long id;
    private String assetType;
    private String opType;
    private String targetId;
    private String payload;
    private Status status;
    private String applicant;
    private LocalDateTime applyTime;
    private String approver;
    private LocalDateTime approveTime;
    private String rejectReason;

    private ApprovalRequest() {
    }

    public static ApprovalRequest submit(String assetType, String opType, String targetId,
                                         String payload, String applicant) {
        ApprovalRequest r = new ApprovalRequest();
        r.assetType = assetType;
        r.opType = opType;
        r.targetId = targetId;
        r.payload = payload;
        r.applicant = applicant;
        r.status = Status.PENDING;
        r.applyTime = LocalDateTime.now();
        r.validate();
        return r;
    }

    private void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (assetType == null || assetType.isBlank()) {
            errors.field("assetType", "必填");
        }
        if (opType == null || opType.isBlank()) {
            errors.field("opType", "必填");
        }
        if (applicant == null || applicant.isBlank()) {
            errors.field("applicant", "必填");
        }
        if (("CREATE".equals(opType) || "UPDATE".equals(opType))
                && (payload == null || payload.isBlank())) {
            errors.field("payload", "CREATE/UPDATE 操作必须提供变更内容");
        }
        errors.throwIfAny();
    }

    /** 审批通过：仅 PENDING 可通过，否则拒绝（已审结不可再变）。 */
    public void approve(String approver) {
        ensurePending();
        this.status = Status.APPROVED;
        this.approver = approver;
        this.approveTime = LocalDateTime.now();
    }

    /** 审批驳回：仅 PENDING 可驳回。 */
    public void reject(String approver, String reason) {
        ensurePending();
        this.status = Status.REJECTED;
        this.approver = approver;
        this.approveTime = LocalDateTime.now();
        this.rejectReason = reason;
    }

    private void ensurePending() {
        if (status != Status.PENDING) {
            throw BizException.invalidState("申请已审结，状态为 " + status + "，不可再次审批");
        }
    }

    public void assignId(Long id) {
        this.id = id;
    }

    // —— 用于仓储重建 ——
    public static ApprovalRequest rehydrate(Long id, String assetType, String opType, String targetId,
                                            String payload, Status status, String applicant,
                                            LocalDateTime applyTime, String approver,
                                            LocalDateTime approveTime, String rejectReason) {
        ApprovalRequest r = new ApprovalRequest();
        r.id = id;
        r.assetType = assetType;
        r.opType = opType;
        r.targetId = targetId;
        r.payload = payload;
        r.status = status;
        r.applicant = applicant;
        r.applyTime = applyTime;
        r.approver = approver;
        r.approveTime = approveTime;
        r.rejectReason = rejectReason;
        return r;
    }

    public Long getId() {
        return id;
    }

    public String getAssetType() {
        return assetType;
    }

    public String getOpType() {
        return opType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public String getApplicant() {
        return applicant;
    }

    public LocalDateTime getApplyTime() {
        return applyTime;
    }

    public String getApprover() {
        return approver;
    }

    public LocalDateTime getApproveTime() {
        return approveTime;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
