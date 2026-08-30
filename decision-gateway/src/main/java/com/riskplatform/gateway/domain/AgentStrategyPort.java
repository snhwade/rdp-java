package com.riskplatform.gateway.domain;

import java.util.List;

/**
 * 按事件类型加载 Agent 策略配置。
 */
public interface AgentStrategyPort {

    ResolvedAgentStrategy resolve(String eventTypeCode);

    record ResolvedAgentStrategy(
            String code,
            String name,
            String configJson,
            String adoptionMode) {

        public ResolvedAgentStrategy(String code, String name, String configJson) {
            this(code, name, configJson, "SHADOW");
        }
    }
}
