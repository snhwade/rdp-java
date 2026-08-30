package com.riskplatform.ruleconfig.domain.approval;

import java.util.List;
import java.util.Optional;

/** 审批申请仓储端口（S5）。 */
public interface ApprovalRequestRepository {

    ApprovalRequest save(ApprovalRequest request);

    ApprovalRequest update(ApprovalRequest request);

    Optional<ApprovalRequest> findById(Long id);

    /** 按状态查询（status 为空查全部）。 */
    List<ApprovalRequest> findByStatus(ApprovalRequest.Status status);

    /** 按发起人查询。 */
    List<ApprovalRequest> findByApplicant(String applicant);
}
