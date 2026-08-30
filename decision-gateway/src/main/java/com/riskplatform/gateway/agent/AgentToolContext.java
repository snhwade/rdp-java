package com.riskplatform.gateway.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 单次推理上下文（工具共享状态 + trace）。
 */
public class AgentToolContext {

    private final String eventId;
    private final String eventTypeCode;
    private final Map<String, Object> context;
    private final String engineDecision;
    private final List<Map<String, Object>> trace;
    private final Map<String, Object> signals = new HashMap<>();

    public AgentToolContext(
            String eventId,
            String eventTypeCode,
            Map<String, Object> context,
            String engineDecision,
            List<Map<String, Object>> trace) {
        this.eventId = eventId;
        this.eventTypeCode = eventTypeCode;
        this.context = context == null ? Map.of() : context;
        this.engineDecision = engineDecision;
        this.trace = trace;
    }

    public String eventId() {
        return eventId;
    }

    public String eventTypeCode() {
        return eventTypeCode;
    }

    public Map<String, Object> context() {
        return context;
    }

    public String engineDecision() {
        return engineDecision;
    }

    public List<Map<String, Object>> trace() {
        return trace;
    }

    public Map<String, Object> signals() {
        return signals;
    }

    public void addTrace(String tool, Map<String, Object> output) {
        Map<String, Object> step = new HashMap<>();
        step.put("tool", tool);
        step.put("output", output);
        trace.add(step);
    }
}
