package com.riskplatform.ruleconfig.infrastructure.indicator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionSnapshot;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class IndicatorDefinitionSnapshotRepositoryImpl implements IndicatorDefinitionSnapshotRepository {

    private final IndicatorDefinitionSnapshotMapper mapper;

    public IndicatorDefinitionSnapshotRepositoryImpl(IndicatorDefinitionSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public IndicatorDefinitionSnapshot save(IndicatorDefinitionSnapshot snapshot) {
        IndicatorDefinitionSnapshotPO po = new IndicatorDefinitionSnapshotPO();
        po.setIndicatorDefinitionId(snapshot.getIndicatorDefinitionId());
        po.setVersion(snapshot.getVersion());
        po.setSnapshotJson(snapshot.getSnapshotJson());
        po.setCreatedBy(snapshot.getCreatedBy());
        mapper.insert(po);
        snapshot.assignId(po.getId());
        snapshot.assignCreatedAt(po.getCreatedAt());
        return snapshot;
    }

    @Override
    public int findMaxVersion(Long indicatorDefinitionId) {
        IndicatorDefinitionSnapshotPO latest = mapper.selectList(new LambdaQueryWrapper<IndicatorDefinitionSnapshotPO>()
                        .eq(IndicatorDefinitionSnapshotPO::getIndicatorDefinitionId, indicatorDefinitionId)
                        .orderByDesc(IndicatorDefinitionSnapshotPO::getVersion)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        return latest == null || latest.getVersion() == null ? 0 : latest.getVersion();
    }

    @Override
    public List<IndicatorDefinitionSnapshot> findByIndicatorDefinitionId(Long indicatorDefinitionId) {
        return mapper.selectList(new LambdaQueryWrapper<IndicatorDefinitionSnapshotPO>()
                        .eq(IndicatorDefinitionSnapshotPO::getIndicatorDefinitionId, indicatorDefinitionId)
                        .orderByDesc(IndicatorDefinitionSnapshotPO::getVersion))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<IndicatorDefinitionSnapshot> findByIndicatorDefinitionIdAndVersion(
            Long indicatorDefinitionId, int version) {
        return mapper.selectList(new LambdaQueryWrapper<IndicatorDefinitionSnapshotPO>()
                        .eq(IndicatorDefinitionSnapshotPO::getIndicatorDefinitionId, indicatorDefinitionId)
                        .eq(IndicatorDefinitionSnapshotPO::getVersion, version))
                .stream().findFirst().map(this::toDomain);
    }

    private IndicatorDefinitionSnapshot toDomain(IndicatorDefinitionSnapshotPO po) {
        IndicatorDefinitionSnapshot s = new IndicatorDefinitionSnapshot(
                po.getIndicatorDefinitionId(), po.getVersion(), po.getSnapshotJson(), po.getCreatedBy());
        s.assignId(po.getId());
        s.assignCreatedAt(po.getCreatedAt());
        return s;
    }
}
