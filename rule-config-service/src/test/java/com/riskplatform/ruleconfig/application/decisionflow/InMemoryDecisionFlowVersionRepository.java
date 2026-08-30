package com.riskplatform.ruleconfig.application.decisionflow;

import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowVersion;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowVersionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;

/** 决策流版本仓储的内存实现（单元测试用）。 */
class InMemoryDecisionFlowVersionRepository implements DecisionFlowVersionRepository {

    private final List<DecisionFlowVersion> store = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public DecisionFlowVersion save(DecisionFlowVersion version) {
        version.assignId(seq.incrementAndGet());
        version.assignCreatedAt(LocalDateTime.now());
        store.add(version);
        return version;
    }

    @Override
    public int findMaxVersion(Long decisionFlowId) {
        return store.stream()
                .filter(v -> v.getDecisionFlowId().equals(decisionFlowId))
                .mapToInt(DecisionFlowVersion::getVersion)
                .max().orElse(0);
    }

    @Override
    public List<DecisionFlowVersion> findByDecisionFlowId(Long decisionFlowId) {
        return store.stream()
                .filter(v -> v.getDecisionFlowId().equals(decisionFlowId))
                .sorted(Comparator.comparingInt(DecisionFlowVersion::getVersion).reversed())
                .toList();
    }

    @Override
    public Optional<DecisionFlowVersion> findByDecisionFlowIdAndVersion(Long decisionFlowId, int version) {
        return store.stream()
                .filter(v -> v.getDecisionFlowId().equals(decisionFlowId) && v.getVersion() == version)
                .findFirst();
    }

    @Override
    public Optional<DecisionFlowVersion> findOnlineVersion(Long decisionFlowId) {
        return store.stream()
                .filter(v -> v.getDecisionFlowId().equals(decisionFlowId) && v.isOnline())
                .findFirst();
    }

    @Override
    public Set<Long> findOnlineFlowIds(java.util.Collection<Long> flowIds) {
        if (flowIds == null || flowIds.isEmpty()) {
            return Set.of();
        }
        return store.stream()
                .filter(v -> flowIds.contains(v.getDecisionFlowId()) && v.isOnline())
                .map(DecisionFlowVersion::getDecisionFlowId)
                .collect(Collectors.toSet());
    }

    @Override
    public void updateStatus(Long decisionFlowId, int version, String status) {
        store.stream()
                .filter(v -> v.getDecisionFlowId().equals(decisionFlowId) && v.getVersion() == version)
                .forEach(v -> {
                    if (DecisionFlowVersion.STATUS_ONLINE.equals(status)) {
                        v.online();
                    } else {
                        v.offline();
                    }
                });
    }
}
