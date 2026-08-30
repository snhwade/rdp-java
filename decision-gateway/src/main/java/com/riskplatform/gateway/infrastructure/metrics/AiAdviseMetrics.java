package com.riskplatform.gateway.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * AI 审核结果计数（enhancement-plan T5）。
 */
public class AiAdviseMetrics {

    private final Counter success;
    private final Counter fail;
    private final Counter llmUnavailable;
    private final Counter heuristicFallback;

    public AiAdviseMetrics(MeterRegistry registry) {
        this.success = Counter.builder("ai_advise_success_total")
                .description("AI advise completed successfully")
                .register(registry);
        this.fail = Counter.builder("ai_advise_fail_total")
                .description("AI advise failed")
                .register(registry);
        this.llmUnavailable = Counter.builder("ai_llm_unavailable_total")
                .description("LLM unavailable during advise")
                .register(registry);
        this.heuristicFallback = Counter.builder("ai_heuristic_fallback_total")
                .description("Fell back to heuristic/default when LLM unavailable")
                .register(registry);
    }

    public void success() {
        success.increment();
    }

    public void fail() {
        fail.increment();
    }

    public void llmUnavailable() {
        llmUnavailable.increment();
    }

    public void heuristicFallback() {
        heuristicFallback.increment();
    }
}
