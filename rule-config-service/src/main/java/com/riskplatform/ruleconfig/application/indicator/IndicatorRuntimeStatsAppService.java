package com.riskplatform.ruleconfig.application.indicator;

import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinition;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionRepository;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorRuntimeStats;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorRuntimeStatsRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 指标运行统计查询（IS1）。 */
@Service
public class IndicatorRuntimeStatsAppService {

    private final IndicatorDefinitionRepository definitionRepository;
    private final IndicatorRuntimeStatsRepository statsRepository;

    public IndicatorRuntimeStatsAppService(IndicatorDefinitionRepository definitionRepository,
                                           IndicatorRuntimeStatsRepository statsRepository) {
        this.definitionRepository = definitionRepository;
        this.statsRepository = statsRepository;
    }

    public List<RuntimeStatsView> listByGroupId(Long groupId) {
        List<IndicatorDefinition> defs = definitionRepository.findAll(groupId, false, null, null);
        Map<String, IndicatorRuntimeStats> statsByRef = statsRepository.findByRefNames(
                defs.stream().map(IndicatorDefinition::getRefName).toList());
        return defs.stream()
                .map(d -> toView(d, statsByRef.get(d.getRefName())))
                .toList();
    }

    public RuntimeStatsView getByRefName(String refName) {
        IndicatorDefinition def = definitionRepository.findByRefName(refName)
                .orElse(null);
        IndicatorRuntimeStats stats = statsRepository.findByRefNames(List.of(refName)).get(refName);
        if (def == null && stats == null) {
            return new RuntimeStatsView(refName, null, null, 0L, null);
        }
        return toView(def, stats);
    }

    private RuntimeStatsView toView(IndicatorDefinition def, IndicatorRuntimeStats stats) {
        String refName = def != null ? def.getRefName() : (stats != null ? stats.refName() : null);
        String status = def != null ? def.getStatus() : null;
        Instant lastAccumulateAt = stats != null ? stats.lastAccumulateAt() : null;
        long readMissCount = stats != null ? stats.readMissCount() : 0L;
        return new RuntimeStatsView(refName, status, lastAccumulateAt, readMissCount, def != null ? def.getId() : null);
    }

    public record RuntimeStatsView(
            String refName,
            String status,
            Instant lastAccumulateAt,
            long readMissCount,
            Long indicatorDefinitionId) {
    }
}
