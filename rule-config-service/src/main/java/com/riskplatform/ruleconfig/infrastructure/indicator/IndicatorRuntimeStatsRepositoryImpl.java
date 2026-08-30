package com.riskplatform.ruleconfig.infrastructure.indicator;

import com.riskplatform.ruleconfig.domain.indicator.IndicatorRuntimeStats;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorRuntimeStatsRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class IndicatorRuntimeStatsRepositoryImpl implements IndicatorRuntimeStatsRepository {

    private final IndicatorRuntimeStatsMapper statsMapper;

    public IndicatorRuntimeStatsRepositoryImpl(IndicatorRuntimeStatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    @Override
    public Map<String, IndicatorRuntimeStats> findByRefNames(List<String> refNames) {
        if (refNames == null || refNames.isEmpty()) {
            return Map.of();
        }
        Map<String, IndicatorRuntimeStats> result = new HashMap<>();
        for (IndicatorRuntimeStatsRow row : statsMapper.findByRefNames(refNames)) {
            result.put(row.getRefName(), toDomain(row));
        }
        return result;
    }

    @Override
    public List<IndicatorRuntimeStats> findByGroupId(Long groupId) {
        return statsMapper.findByGroupId(groupId).stream().map(this::toDomain).toList();
    }

    private IndicatorRuntimeStats toDomain(IndicatorRuntimeStatsRow row) {
        Instant lastAccumulateAt = row.getLastAccumulateAt() == null
                ? null
                : row.getLastAccumulateAt().toInstant(ZoneOffset.UTC);
        return new IndicatorRuntimeStats(row.getRefName(), lastAccumulateAt, row.getReadMissCount());
    }
}
