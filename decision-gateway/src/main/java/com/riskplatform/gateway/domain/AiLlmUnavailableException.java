package com.riskplatform.gateway.domain;

/**
 * LLM 不可用且未允许启发式回退时抛出，由 DecisionExecutionLogService 记为 FAILED。
 */
public class AiLlmUnavailableException extends RuntimeException {

    public AiLlmUnavailableException(String message) {
        super(message);
    }
}
