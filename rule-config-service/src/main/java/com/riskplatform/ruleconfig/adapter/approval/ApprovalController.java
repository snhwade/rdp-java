package com.riskplatform.ruleconfig.adapter.approval;

import com.riskplatform.ruleconfig.application.approval.ApprovalService;
import com.riskplatform.ruleconfig.domain.approval.ApprovalRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 复核审批 REST 适配器（S5，Maker-Checker）。
 *
 * <ul>
 *   <li>POST /api/v1/approvals                 提交变更草稿（待审）</li>
 *   <li>GET  /api/v1/approvals?status=&applicant= 查询（待审列表 / 我发起的）</li>
 *   <li>POST /api/v1/approvals/{id}/approve     审批通过</li>
 *   <li>POST /api/v1/approvals/{id}/reject      审批驳回</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @PostMapping
    public ApprovalRequest submit(@RequestBody SubmitRequest req) {
        return service.submit(req.assetType(), req.opType(), req.targetId(), req.payload(), req.applicant());
    }

    @GetMapping
    public List<ApprovalRequest> list(@RequestParam(name = "status", required = false) String status,
                                      @RequestParam(name = "applicant", required = false) String applicant) {
        if (applicant != null && !applicant.isBlank()) {
            return service.listByApplicant(applicant);
        }
        ApprovalRequest.Status s = (status == null || status.isBlank())
                ? null : ApprovalRequest.Status.valueOf(status);
        return service.listByStatus(s);
    }

    @PostMapping("/{id}/approve")
    public ApprovalRequest approve(@PathVariable("id") Long id, @RequestBody ApproveRequest req) {
        return service.approve(id, req.approver());
    }

    @PostMapping("/{id}/reject")
    public ApprovalRequest reject(@PathVariable("id") Long id, @RequestBody RejectRequest req) {
        return service.reject(id, req.approver(), req.reason());
    }

    public record SubmitRequest(
            @NotBlank String assetType,
            @NotBlank String opType,
            String targetId,
            String payload,
            @NotBlank String applicant) {
    }

    public record ApproveRequest(@NotBlank String approver) {
    }

    public record RejectRequest(@NotBlank String approver, String reason) {
    }
}
