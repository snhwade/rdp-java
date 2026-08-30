package com.riskplatform.gateway.infrastructure.standalone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.gateway.domain.AgentStrategyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class DbAgentStrategyLoader implements AgentStrategyPort {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final AgentStrategyReadMapper mapper;
    private final ObjectMapper objectMapper;

    public DbAgentStrategyLoader(AgentStrategyReadMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResolvedAgentStrategy resolve(String eventTypeCode) {
        AgentStrategyRow fallback = null;
        for (AgentStrategyRow row : mapper.selectAll()) {
            if (!"ENABLED".equals(row.status())) {
                continue;
            }
            List<String> codes = parseCodes(row.eventTypeCodes());
            if (codes.contains("*")) {
                fallback = row;
                continue;
            }
            if (eventTypeCode != null && codes.contains(eventTypeCode)) {
                return toResolved(row);
            }
        }
        return fallback == null ? null : toResolved(fallback);
    }

    private ResolvedAgentStrategy toResolved(AgentStrategyRow row) {
        return new ResolvedAgentStrategy(row.code(), row.name(),
                row.configJson() == null ? "{}" : row.configJson(),
                row.adoptionMode() == null || row.adoptionMode().isBlank() ? "SHADOW" : row.adoptionMode());
    }

    private List<String> parseCodes(String json) {
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
