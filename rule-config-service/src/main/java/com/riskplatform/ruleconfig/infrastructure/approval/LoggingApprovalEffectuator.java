package com.riskplatform.ruleconfig.infrastructure.approval;

import com.riskplatform.ruleconfig.domain.approval.ApprovalEffectuator;
import com.riskplatform.ruleconfig.domain.approval.ApprovalRequest;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 审批生效回调默认实现（S5）。
 *
 * <p>审批通过后记录日志并广播配置变更（按 assetType 通知引擎刷新缓存）。真正写目标资产表的逻辑
 * 在本故事保持解耦（由各资产服务在拿到 APPROVED 后执行），此处以「记录 + 广播」表达生效闭环。
 */
public class LoggingApprovalEffectuator implements ApprovalEffectuator {

    private static final Logger log = LoggerFactory.getLogger(LoggingApprovalEffectuator.class);

    private final ConfigChangePublisher configChangePublisher;

    public LoggingApprovalEffectuator(ConfigChangePublisher configChangePublisher) {
        this.configChangePublisher = configChangePublisher;
    }

    @Override
    public void effectuate(ApprovalRequest request) {
        log.info("审批通过生效: assetType={} opType={} targetId={} approver={}",
                request.getAssetType(), request.getOpType(), request.getTargetId(), request.getApprover());
        // 广播配置变更，使引擎/消费侧刷新（尽力而为）
        configChangePublisher.publishChange(request.getAssetType(),
                request.getTargetId() == null ? String.valueOf(request.getId()) : request.getTargetId());
    }
}
