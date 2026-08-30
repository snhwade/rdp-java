package com.riskplatform.ruleconfig.domain.indicator;

import java.util.List;
import java.util.Map;

public interface IndicatorRuntimeStatsRepository {

    Map<String, IndicatorRuntimeStats> findByRefNames(List<String> refNames);

    List<IndicatorRuntimeStats> findByGroupId(Long groupId);
}
