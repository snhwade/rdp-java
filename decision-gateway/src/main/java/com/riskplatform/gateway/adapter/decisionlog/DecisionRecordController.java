package com.riskplatform.gateway.adapter.decisionlog;

import com.riskplatform.common.model.PagedResult;
import com.riskplatform.gateway.application.DecisionExecutionLogService;
import com.riskplatform.gateway.application.InvocationTraceService;
import com.riskplatform.gateway.domain.DecisionRecordQuery;
import com.riskplatform.gateway.domain.InvocationDetailView;
import com.riskplatform.gateway.domain.InvocationTraceView;
import com.riskplatform.gateway.domain.UnifiedDecisionRecordView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调用维度决策查询：每次风控检查（eventId）一条记录；详情含引擎命中与 AI 推理过程。
 */
@RestController
@RequestMapping("/api/v1/decision-records")
public class DecisionRecordController {

    private final DecisionExecutionLogService service;
    private final InvocationTraceService traceService;

    public DecisionRecordController(DecisionExecutionLogService service,
                                      InvocationTraceService traceService) {
        this.service = service;
        this.traceService = traceService;
    }

    @GetMapping
    public PagedResult<UnifiedDecisionRecordView> query(
            @RequestParam(name = "eventId", required = false) String eventId,
            @RequestParam(name = "correlationId", required = false) String correlationId,
            @RequestParam(name = "businessOrderId", required = false) String businessOrderId,
            @RequestParam(name = "merchantId", required = false) String merchantId,
            @RequestParam(name = "eventTypeCode", required = false) String eventTypeCode,
            @RequestParam(name = "startTimeMs", required = false) Long startTimeMs,
            @RequestParam(name = "endTimeMs", required = false) Long endTimeMs,
            @RequestParam(name = "divergenceOnly", required = false) Boolean divergenceOnly,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "20") int pageSize) {
        return service.queryDecisionRecords(new DecisionRecordQuery(
                eventId, correlationId, merchantId, eventTypeCode, startTimeMs, endTimeMs, page, pageSize,
                businessOrderId, divergenceOnly));
    }

    @GetMapping("/{eventId}")
    public InvocationDetailView getDetail(@PathVariable String eventId) {
        return service.getInvocationDetail(eventId);
    }

    @GetMapping("/{eventId}/trace")
    public InvocationTraceView getTrace(@PathVariable String eventId) {
        return traceService.queryByEventId(eventId);
    }
}

