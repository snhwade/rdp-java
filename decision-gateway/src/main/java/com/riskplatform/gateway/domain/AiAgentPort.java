package com.riskplatform.gateway.domain;


import java.util.Map;

/**
 * AI Agent 事中推理端口（异步旁路）。
 */
public interface AiAgentPort {

    /**
     * 基于事件上下文自主推理产出 Agent 决策（可含工具调用 trace）。
     */
    AiAdviseResult advise(
            String eventId,
            String eventTypeCode,
            Map<String, Object> context,
            String engineDecision);
}
