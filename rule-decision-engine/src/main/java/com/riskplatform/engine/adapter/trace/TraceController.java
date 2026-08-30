package com.riskplatform.engine.adapter.trace;

import com.riskplatform.engine.application.TraceQueryService;
import com.riskplatform.engine.application.TraceView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 执行链路查询 REST 适配器（R15.3/R15.4）。
 *
 * <p>对外暴露 {@code GET /api/v1/trace/{eventId}}：返回规则匹配 / 规则执行 / 决策聚合的完整链路记录。
 * 事件不存在时由 {@code GlobalExceptionHandler} 映射为 404。
 *
 * <p>注：{@link PathVariable} 显式指定 {@code name}，避免依赖编译期保留参数名。
 */
@RestController
@RequestMapping("/api/v1/trace")
public class TraceController {

    private final TraceQueryService traceQueryService;

    public TraceController(TraceQueryService traceQueryService) {
        this.traceQueryService = traceQueryService;
    }

    /**
     * 按事件标识查询执行链路。
     *
     * @param eventId 事件标识
     * @return 链路视图（规则匹配/规则执行/决策聚合）
     */
    @GetMapping("/{eventId}")
    public TraceView trace(@PathVariable("eventId") String eventId) {
        return traceQueryService.query(eventId);
    }
}
