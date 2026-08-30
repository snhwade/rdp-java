package com.riskplatform.ruleconfig.infrastructure.rulepackage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackageEnabledSnapshot;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackageEnabledSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RulePackageEnabledSnapshotRepositoryImpl implements RulePackageEnabledSnapshotRepository {

    private final RulePackageEnabledSnapshotMapper mapper;

    public RulePackageEnabledSnapshotRepositoryImpl(RulePackageEnabledSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RulePackageEnabledSnapshot save(RulePackageEnabledSnapshot snapshot) {
        RulePackageEnabledSnapshotPO po = new RulePackageEnabledSnapshotPO();
        po.setRulePackageId(snapshot.getRulePackageId());
        po.setVersion(snapshot.getVersion());
        po.setSnapshotJson(snapshot.getSnapshotJson());
        po.setCreatedBy(snapshot.getCreatedBy());
        mapper.insert(po);
        snapshot.assignId(po.getId());
        snapshot.assignCreatedAt(po.getCreatedAt());
        return snapshot;
    }

    @Override
    public int findMaxVersion(Long rulePackageId) {
        RulePackageEnabledSnapshotPO latest = mapper.selectList(new LambdaQueryWrapper<RulePackageEnabledSnapshotPO>()
                        .eq(RulePackageEnabledSnapshotPO::getRulePackageId, rulePackageId)
                        .orderByDesc(RulePackageEnabledSnapshotPO::getVersion)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        return latest == null || latest.getVersion() == null ? 0 : latest.getVersion();
    }

    @Override
    public List<RulePackageEnabledSnapshot> findByRulePackageId(Long rulePackageId) {
        return mapper.selectList(new LambdaQueryWrapper<RulePackageEnabledSnapshotPO>()
                        .eq(RulePackageEnabledSnapshotPO::getRulePackageId, rulePackageId)
                        .orderByDesc(RulePackageEnabledSnapshotPO::getVersion))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<RulePackageEnabledSnapshot> findByRulePackageIdAndVersion(Long rulePackageId, int version) {
        return mapper.selectList(new LambdaQueryWrapper<RulePackageEnabledSnapshotPO>()
                        .eq(RulePackageEnabledSnapshotPO::getRulePackageId, rulePackageId)
                        .eq(RulePackageEnabledSnapshotPO::getVersion, version))
                .stream().findFirst().map(this::toDomain);
    }

    private RulePackageEnabledSnapshot toDomain(RulePackageEnabledSnapshotPO po) {
        RulePackageEnabledSnapshot s = new RulePackageEnabledSnapshot(
                po.getRulePackageId(), po.getVersion(), po.getSnapshotJson(), po.getCreatedBy());
        s.assignId(po.getId());
        s.assignCreatedAt(po.getCreatedAt());
        return s;
    }
}
