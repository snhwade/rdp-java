package com.riskplatform.gateway.infrastructure.standalone;

record AgentStrategyRow(
        String code,
        String name,
        String eventTypeCodes,
        String configJson,
        String status,
        String adoptionMode) {
}
