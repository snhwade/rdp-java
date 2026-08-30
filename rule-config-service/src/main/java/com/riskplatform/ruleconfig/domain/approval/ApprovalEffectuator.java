package com.riskplatform.ruleconfig.domain.approval;

/**
 * 审批生效回调端口（S5）。
 *
 * <p>审批通过后调用，用于触发变更真正生效（写目标表 / 广播失效）。本故事提供默认日志实现，
 * 保持审批模块与各资产解耦；后续可按 assetType 路由到对应资产的生效逻辑。
 */
public interface ApprovalEffectuator {

    /** 审批通过后触发变更生效。 */
    void effectuate(ApprovalRequest request);
}
