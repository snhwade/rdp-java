package com.riskplatform.gateway.adapter.decisionlog;

import com.riskplatform.common.model.PagedResult;
import com.riskplatform.gateway.application.DecisionExecutionLogService;
import com.riskplatform.gateway.application.InvocationTraceService;
import com.riskplatform.gateway.domain.DecisionRecordQuery;
import com.riskplatform.gateway.domain.EngineDecisionRecordView;
import com.riskplatform.gateway.domain.EngineDecisionStatsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 引擎决策执行记录查询（管理端）。
 */
@RestController
@RequestMapping("/api/v1/engine-decision-records")
public class EngineDecisionRecordController {

    private final DecisionExecutionLogService service;
    private final InvocationTraceService traceService;

    public EngineDecisionRecordController(DecisionExecutionLogService service,
                                            InvocationTraceService traceService) {
        this.service = service;
        this.traceService = traceService;
    }

    @GetMapping("/stats")
    public EngineDecisionStatsView stats(
            @RequestParam(name = "startTimeMs", required = false) Long startTimeMs,
            @RequestParam(name = "endTimeMs", required = false) Long endTimeMs,
            @RequestParam(name = "eventTypeCode", required = false) String eventTypeCode) {
        return traceService.queryEngineStats(startTimeMs, endTimeMs, eventTypeCode);
    }

    @GetMapping
    public PagedResult<EngineDecisionRecordView> query(
            @RequestParam(name = "eventId", required = false) String eventId,
            @RequestParam(name = "correlationId", required = false) String correlationId,
            @RequestParam(name = "merchantId", required = false) String merchantId,
            @RequestParam(name = "eventTypeCode", required = false) String eventTypeCode,
            @RequestParam(name = "startTimeMs", required = false) Long startTimeMs,
            @RequestParam(name = "endTimeMs", required = false) Long endTimeMs,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "20") int pageSize) {
        return service.queryEngine(new DecisionRecordQuery(
                eventId, correlationId, merchantId, eventTypeCode, startTimeMs, endTimeMs, page, pageSize));
    }

    @GetMapping("/{eventId}")
    public EngineDecisionRecordView get(@PathVariable String eventId) {
        return service.getEngine(eventId);
    }
}
