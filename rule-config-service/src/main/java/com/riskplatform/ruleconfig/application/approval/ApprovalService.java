package com.riskplatform.ruleconfig.application.approval;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.approval.ApprovalEffectuator;
import com.riskplatform.ruleconfig.domain.approval.ApprovalRequest;
import com.riskplatform.ruleconfig.domain.approval.ApprovalRequestRepository;

import java.util.List;

/**
 * 复核审批应用服务（S5）：提交草稿 / 审批通过 / 驳回 / 查询。
 *
 * <p>审批通过后调用生效回调 {@link ApprovalEffectuator} 触发变更真正生效。
 */
public class ApprovalService {

    private final ApprovalRequestRepository repository;
    private final ApprovalEffectuator effectuator;

    public ApprovalService(ApprovalRequestRepository repository, ApprovalEffectuator effectuator) {
        this.repository = repository;
        this.effectuator = effectuator;
    }

    /** 提交变更草稿，进入待审。 */
    public ApprovalRequest submit(String assetType, String opType, String targetId,
                                  String payload, String applicant) {
        ApprovalRequest r = ApprovalRequest.submit(assetType, opType, targetId, payload, applicant);
        return repository.save(r);
    }

    /** 审批通过：状态流转 + 触发生效回调。 */
    public ApprovalRequest approve(Long id, String approver) {
        ApprovalRequest r = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("审批申请不存在: id=" + id));
        r.approve(approver);          // 状态机校验（仅 PENDING 可通过）
        ApprovalRequest saved = repository.update(r);
        effectuator.effectuate(saved); // 通过后触发变更生效
        return saved;
    }

    /** 审批驳回。 */
    public ApprovalRequest reject(Long id, String approver, String reason) {
        ApprovalRequest r = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("审批申请不存在: id=" + id));
        r.reject(approver, reason);
        return repository.update(r);
    }

    public List<ApprovalRequest> listByStatus(ApprovalRequest.Status status) {
        return repository.findByStatus(status);
    }

    public List<ApprovalRequest> listByApplicant(String applicant) {
        return repository.findByApplicant(applicant);
    }
}
