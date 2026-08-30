package com.riskplatform.engine.adapter.decision;

import com.riskplatform.engine.application.DecisionLogService;
import com.riskplatform.engine.domain.decision.DecisionLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 决策结果查询 REST 适配器（R15.3）。
 *
 * <p>提供 {@code GET /api/v1/decisions/{eventId}} 按事件标识查询历史决策日志。
 */
@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {

    private final DecisionLogService decisionLogService;

    public DecisionController(DecisionLogService decisionLogService) {
        this.decisionLogService = decisionLogService;
    }

    @GetMapping("/{eventId}")
    public EvaluateView get(@PathVariable("eventId") String eventId) {
        DecisionLog log = decisionLogService.query(eventId)
                .orElseThrow(() -> com.riskplatform.common.error.BizException.notFound(
                        "决策结果不存在: " + eventId));
        return new EvaluateView(
                log.eventId(),
                log.finalDecision() == null ? null : log.finalDecision().name(),
                log.groupStatus() == null ? null : log.groupStatus().name(),
                log.elapsedMs(),
                log.hitDecisions().stream()
                        .map(h -> new HitView(h.ruleId(), h.priority(), h.decision().name()))
                        .toList());
    }

    public record HitView(long ruleId, int priority, String decision) {
    }

    /** 决策评估结果视图。 */
    public record EvaluateView(String eventId, String decision, String groupStatus,
                               long elapsedMs, List<HitView> hits) {
    }
}
