package com.riskplatform.ruleconfig.domain.decisionflow;

import java.util.List;
import java.util.Optional;

/** 决策流版本仓储端口（扩展阶段，R6.5）。 */
public interface DecisionFlowVersionRepository {

    /** 保存一条版本快照。 */
    DecisionFlowVersion save(DecisionFlowVersion version);

    /** 查询某决策流当前最大版本号；无历史返回 0。 */
    int findMaxVersion(Long decisionFlowId);

    /** 按决策流查询版本列表（版本号降序）。 */
    List<DecisionFlowVersion> findByDecisionFlowId(Long decisionFlowId);

    /** 查询某决策流的指定版本。 */
    Optional<DecisionFlowVersion> findByDecisionFlowIdAndVersion(Long decisionFlowId, int version);

    /** 查询某决策流当前处于已上线（ONLINE）状态的版本（至多一个）。 */
    Optional<DecisionFlowVersion> findOnlineVersion(Long decisionFlowId);

    /** 批量查询当前处于 ONLINE 状态的决策流 id。 */
    java.util.Set<Long> findOnlineFlowIds(java.util.Collection<Long> flowIds);

    /** 更新某版本的上下线状态。 */
    void updateStatus(Long decisionFlowId, int version, String status);
}
