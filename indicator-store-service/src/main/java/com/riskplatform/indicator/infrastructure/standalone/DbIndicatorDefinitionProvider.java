package com.riskplatform.indicator.infrastructure.standalone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.indicator.application.accumulate.IndicatorDefinition;
import com.riskplatform.indicator.application.accumulate.IndicatorDefinitionCatalog;
import com.riskplatform.indicator.domain.SliceGranularity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class DbIndicatorDefinitionProvider implements IndicatorDefinitionCatalog {

    private static final Logger log = LoggerFactory.getLogger(DbIndicatorDefinitionProvider.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final IndicatorDefinitionReadMapper mapper;
    private final ObjectMapper objectMapper;
    private final AtomicReference<List<IndicatorDefinition>> snapshot = new AtomicReference<>(List.of());

    public DbIndicatorDefinitionProvider(IndicatorDefinitionReadMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<IndicatorDefinition> current() {
        return snapshot.get();
    }

    @Override
    public void refresh() {
        try {
            List<IndicatorDefinitionRow> rows = mapper.selectOnline();
            List<IndicatorDefinition> defs = rows.stream().map(this::toDefinition).toList();
            snapshot.set(defs);
            log.info("指标定义已从 DB 刷新，共 {} 个: {}", defs.size(),
                    defs.stream().map(IndicatorDefinition::refName).toList());
        } catch (Exception ex) {
            log.warn("从 DB 拉取指标定义失败，保留上次快照（{} 个）。原因: {}",
                    snapshot.get().size(), ex.getMessage());
        }
    }

    private IndicatorDefinition toDefinition(IndicatorDefinitionRow row) {
        List<String> eventTypeCodes = parseJsonList(row.eventTypeCodes());
        List<String> dimensions = parseJsonList(row.dimensions());
        SliceGranularity granularity = SliceGranularity.valueOf(row.sliceGranularity());
        return new IndicatorDefinition(
                row.refName(),
                eventTypeCodes,
                dimensions,
                granularity,
                row.windowDays() == null ? 1 : row.windowDays(),
                row.accScript() == null ? "current + 1" : row.accScript(),
                true);
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
