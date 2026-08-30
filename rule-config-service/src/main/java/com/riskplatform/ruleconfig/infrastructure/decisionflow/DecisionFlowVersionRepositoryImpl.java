package com.riskplatform.ruleconfig.infrastructure.decisionflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowVersion;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowVersionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 决策流版本仓储 MyBatis-Plus 实现（扩展阶段，R6.5）。 */
@Repository
public class DecisionFlowVersionRepositoryImpl implements DecisionFlowVersionRepository {

    private final DecisionFlowVersionMapper mapper;

    public DecisionFlowVersionRepositoryImpl(DecisionFlowVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DecisionFlowVersion save(DecisionFlowVersion version) {
        DecisionFlowVersionPO po = new DecisionFlowVersionPO();
        po.setDecisionFlowId(version.getDecisionFlowId());
        po.setVersion(version.getVersion());
        po.setSnapshotJson(version.getSnapshotJson());
        po.setStatus(version.getStatus());
        po.setCreatedBy(version.getCreatedBy());
        mapper.insert(po);
        version.assignId(po.getId());
        version.assignCreatedAt(po.getCreatedAt());
        return version;
    }

    @Override
    public int findMaxVersion(Long decisionFlowId) {
        // 取该决策流最新一条（版本号降序），无历史返回 0
        DecisionFlowVersionPO latest = mapper.selectList(new LambdaQueryWrapper<DecisionFlowVersionPO>()
                        .eq(DecisionFlowVersionPO::getDecisionFlowId, decisionFlowId)
                        .orderByDesc(DecisionFlowVersionPO::getVersion))
                .stream().findFirst().orElse(null);
        return latest == null || latest.getVersion() == null ? 0 : latest.getVersion();
    }

    @Override
    public List<DecisionFlowVersion> findByDecisionFlowId(Long decisionFlowId) {
        return mapper.selectList(new LambdaQueryWrapper<DecisionFlowVersionPO>()
                        .eq(DecisionFlowVersionPO::getDecisionFlowId, decisionFlowId)
                        .orderByDesc(DecisionFlowVersionPO::getVersion))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<DecisionFlowVersion> findByDecisionFlowIdAndVersion(Long decisionFlowId, int version) {
        DecisionFlowVersionPO po = mapper.selectList(new LambdaQueryWrapper<DecisionFlowVersionPO>()
                        .eq(DecisionFlowVersionPO::getDecisionFlowId, decisionFlowId)
                        .eq(DecisionFlowVersionPO::getVersion, version))
                .stream().findFirst().orElse(null);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<DecisionFlowVersion> findOnlineVersion(Long decisionFlowId) {
        DecisionFlowVersionPO po = mapper.selectList(new LambdaQueryWrapper<DecisionFlowVersionPO>()
                        .eq(DecisionFlowVersionPO::getDecisionFlowId, decisionFlowId)
                        .eq(DecisionFlowVersionPO::getStatus, DecisionFlowVersion.STATUS_ONLINE))
                .stream().findFirst().orElse(null);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Set<Long> findOnlineFlowIds(java.util.Collection<Long> flowIds) {
        if (flowIds == null || flowIds.isEmpty()) {
            return Set.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<DecisionFlowVersionPO>()
                        .in(DecisionFlowVersionPO::getDecisionFlowId, flowIds)
                        .eq(DecisionFlowVersionPO::getStatus, DecisionFlowVersion.STATUS_ONLINE))
                .stream()
                .map(DecisionFlowVersionPO::getDecisionFlowId)
                .collect(Collectors.toSet());
    }

    @Override
    public void updateStatus(Long decisionFlowId, int version, String status) {
        DecisionFlowVersionPO update = new DecisionFlowVersionPO();
        update.setStatus(status);
        mapper.update(update, new LambdaQueryWrapper<DecisionFlowVersionPO>()
                .eq(DecisionFlowVersionPO::getDecisionFlowId, decisionFlowId)
                .eq(DecisionFlowVersionPO::getVersion, version));
    }

    private DecisionFlowVersion toDomain(DecisionFlowVersionPO po) {
        return DecisionFlowVersion.rehydrate(po.getId(), po.getDecisionFlowId(), po.getVersion(),
                po.getSnapshotJson(), po.getCreatedBy(), po.getCreatedAt(), po.getStatus());
    }
}
