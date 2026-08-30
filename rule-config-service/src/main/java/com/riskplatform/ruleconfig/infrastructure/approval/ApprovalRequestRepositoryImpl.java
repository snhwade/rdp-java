package com.riskplatform.ruleconfig.infrastructure.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.approval.ApprovalRequest;
import com.riskplatform.ruleconfig.domain.approval.ApprovalRequestRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 审批申请仓储 MyBatis-Plus 实现（S5）。 */
@Repository
public class ApprovalRequestRepositoryImpl implements ApprovalRequestRepository {

    private final ApprovalRequestMapper mapper;

    public ApprovalRequestRepositoryImpl(ApprovalRequestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ApprovalRequest save(ApprovalRequest request) {
        ApprovalRequestPO po = toPO(request);
        mapper.insert(po);
        request.assignId(po.getId());
        return request;
    }

    @Override
    public ApprovalRequest update(ApprovalRequest request) {
        mapper.updateById(toPO(request));
        return request;
    }

    @Override
    public Optional<ApprovalRequest> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<ApprovalRequest> findByStatus(ApprovalRequest.Status status) {
        LambdaQueryWrapper<ApprovalRequestPO> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(ApprovalRequestPO::getStatus, status.name());
        }
        w.orderByDesc(ApprovalRequestPO::getId);
        return mapper.selectList(w).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ApprovalRequest> findByApplicant(String applicant) {
        return mapper.selectList(new LambdaQueryWrapper<ApprovalRequestPO>()
                        .eq(ApprovalRequestPO::getApplicant, applicant)
                        .orderByDesc(ApprovalRequestPO::getId))
                .stream().map(this::toDomain).toList();
    }

    private ApprovalRequestPO toPO(ApprovalRequest r) {
        ApprovalRequestPO po = new ApprovalRequestPO();
        po.setId(r.getId());
        po.setAssetType(r.getAssetType());
        po.setOpType(r.getOpType());
        po.setTargetId(r.getTargetId());
        po.setPayload(r.getPayload());
        po.setStatus(r.getStatus().name());
        po.setApplicant(r.getApplicant());
        po.setApplyTime(r.getApplyTime());
        po.setApprover(r.getApprover());
        po.setApproveTime(r.getApproveTime());
        po.setRejectReason(r.getRejectReason());
        return po;
    }

    private ApprovalRequest toDomain(ApprovalRequestPO po) {
        return ApprovalRequest.rehydrate(
                po.getId(), po.getAssetType(), po.getOpType(), po.getTargetId(), po.getPayload(),
                ApprovalRequest.Status.valueOf(po.getStatus()), po.getApplicant(), po.getApplyTime(),
                po.getApprover(), po.getApproveTime(), po.getRejectReason());
    }
}
