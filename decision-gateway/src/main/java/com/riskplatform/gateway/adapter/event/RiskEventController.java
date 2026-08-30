package com.riskplatform.gateway.adapter.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.gateway.application.RiskEventResult;
import com.riskplatform.gateway.application.RiskEventService;
import com.riskplatform.gateway.domain.InvokeMode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 风控事件受理 REST 适配器（R2）。
 *
 * <p>{@code POST /api/v1/risk-events}：受理风控事件，校验通过后生成 eventId、异步落库、
 * 触发引擎决策并返回最终决策。校验失败返回结构化错误体（R2.2-2.5）。
 */
@RestController
@RequestMapping("/api/v1/risk-events")
public class RiskEventController {

    private final RiskEventService riskEventService;
    private final ObjectMapper objectMapper;

    public RiskEventController(RiskEventService riskEventService, ObjectMapper objectMapper) {
        this.riskEventService = riskEventService;
        this.objectMapper = objectMapper;
    }

    /**
     * 受理风控事件。
     *
     * @param request 请求体：{@code { eventTypeCode, context }}
     * @return {@code { eventId, decision }}
     */
    @PostMapping
    @SuppressWarnings("unchecked")
    public RiskEventResult accept(@RequestBody Map<String, Object> request) {
        Object codeObj = request.get("eventTypeCode");
        String eventTypeCode = codeObj == null ? null : String.valueOf(codeObj);

        Object ctxObj = request.get("context");
        Map<String, Object> context = null;
        if (ctxObj instanceof Map<?, ?> map) {
            context = (Map<String, Object>) map;
        } else if (ctxObj != null) {
            throw new BizException(CommonErrorCode.INVALID_FIELD,
                    "context 必须为对象",
                    Map.of("context", "格式非法"));
        }

        // 估算上下文序列化字节数（R2.5 ≤64KB）
        int contextSizeBytes = measureContextBytes(context);

        InvokeMode invokeMode = parseInvokeMode(request.get("invokeMode"));
        Long rulePackageId = longValue(request.get("rulePackageId"));
        Long decisionFlowId = longValue(request.get("decisionFlowId"));

        return riskEventService.accept(
                eventTypeCode, context, contextSizeBytes, invokeMode, rulePackageId, decisionFlowId);
    }

    private int measureContextBytes(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return 0;
        }
        try {
            return objectMapper.writeValueAsBytes(context).length;
        } catch (JsonProcessingException ex) {
            throw new BizException(CommonErrorCode.INVALID_FIELD,
                    "context 序列化失败",
                    Map.of("context", "格式非法"));
        }
    }

    private static InvokeMode parseInvokeMode(Object raw) {
        String value = stringValue(raw);
        if (value == null || value.isBlank()) {
            return InvokeMode.AUTO;
        }
        try {
            return InvokeMode.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(CommonErrorCode.INVALID_FIELD,
                    "invokeMode 非法",
                    Map.of("invokeMode", "不受支持的取值: " + value));
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
