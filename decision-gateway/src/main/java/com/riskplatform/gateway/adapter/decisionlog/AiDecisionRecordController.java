package com.riskplatform.gateway.adapter.decisionlog;

import com.riskplatform.common.model.PagedResult;
import com.riskplatform.gateway.application.DecisionExecutionLogService;
import com.riskplatform.gateway.domain.AiDecisionRecordView;
import com.riskplatform.gateway.domain.AiDecisionStatsView;
import com.riskplatform.gateway.domain.DecisionRecordQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Agent 决策执行记录查询（管理端）。
 */
@RestController
@RequestMapping("/api/v1/ai-decision-records")
public class AiDecisionRecordController {

    private final DecisionExecutionLogService service;

    public AiDecisionRecordController(DecisionExecutionLogService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public AiDecisionStatsView stats(
            @RequestParam(name = "startTimeMs", required = false) Long startTimeMs,
            @RequestParam(name = "endTimeMs", required = false) Long endTimeMs,
            @RequestParam(name = "eventTypeCode", required = false) String eventTypeCode) {
        return service.queryAiStats(startTimeMs, endTimeMs, eventTypeCode);
    }

    @GetMapping
    public PagedResult<AiDecisionRecordView> query(
            @RequestParam(name = "eventId", required = false) String eventId,
            @RequestParam(name = "correlationId", required = false) String correlationId,
            @RequestParam(name = "merchantId", required = false) String merchantId,
            @RequestParam(name = "eventTypeCode", required = false) String eventTypeCode,
            @RequestParam(name = "startTimeMs", required = false) Long startTimeMs,
            @RequestParam(name = "endTimeMs", required = false) Long endTimeMs,
            @RequestParam(name = "divergenceOnly", required = false) Boolean divergenceOnly,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "20") int pageSize) {
        return service.queryAi(new DecisionRecordQuery(
                eventId, correlationId, merchantId, eventTypeCode, startTimeMs, endTimeMs, page, pageSize,
                null, divergenceOnly));
    }

    @GetMapping("/{eventId}")
    public AiDecisionRecordView get(@PathVariable String eventId) {
        return service.getAi(eventId);
    }
}
