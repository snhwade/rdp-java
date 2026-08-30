package com.riskplatform.gateway.domain;

import java.util.Map;

/**
 * 规则/决策引擎网关端口（R2.6/R16.1）。由基础设施层用 REST 客户端或内嵌引擎实现。
 */
public interface EngineGateway {

    /**
     * 触发规则匹配并返回最终决策（默认 AUTO 解析决策流/规则包）。
     */
    default String evaluate(String eventId, String eventTypeCode, Map<String, Object> context) {
        return evaluateDetailed(eventId, eventTypeCode, context, InvokeMode.AUTO, null, null).decision();
    }

    /**
     * 按调用模式执行风控决策。
     */
    EngineEvaluationResult evaluateDetailed(String eventId,
                                            String eventTypeCode,
                                            Map<String, Object> context,
                                            InvokeMode invokeMode,
                                            Long rulePackageId,
                                            Long decisionFlowId);
}
