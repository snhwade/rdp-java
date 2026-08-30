package com.riskplatform.ruleconfig.domain.indicator;

import java.util.List;
import java.util.Optional;

public interface IndicatorDefinitionSnapshotRepository {

    IndicatorDefinitionSnapshot save(IndicatorDefinitionSnapshot snapshot);

    int findMaxVersion(Long indicatorDefinitionId);

    List<IndicatorDefinitionSnapshot> findByIndicatorDefinitionId(Long indicatorDefinitionId);

    Optional<IndicatorDefinitionSnapshot> findByIndicatorDefinitionIdAndVersion(
            Long indicatorDefinitionId, int version);
}
