package com.riskplatform.engine.application;

import com.riskplatform.common.error.BizException;
import com.riskplatform.engine.domain.decision.DecisionLog;
import com.riskplatform.engine.domain.decision.DecisionLogRepository;

/**
 * 执行链路查询应用服务（R15.3/R15.4）。
 *
 * <p>按事件标识从决策日志还原完整执行链路（规则匹配/规则执行/决策聚合），
 * 并以 traceId 关联 eventId 供链路检索。事件不存在时抛出 NOT_FOUND（由全局异常处理器映射 404）。
 *
 * <p>链路追踪标识：接入 Micrometer Tracing/OpenTelemetry 后，traceId 由当前 Span 提供并与
 * eventId 关联；本服务通过可注入的 {@link TraceIdResolver} 获取，缺省回退为 eventId 自身，
 * 保证在未启用分布式追踪时查询仍可用。
 */
public class TraceQueryService {

    /** 链路标识解析端口：根据 eventId 返回关联的 traceId（缺省回退 eventId）。 */
    public interface TraceIdResolver {
        String resolve(String eventId);
    }

    private final DecisionLogRepository decisionLogRepository;
    private final TraceIdResolver traceIdResolver;

    public TraceQueryService(DecisionLogRepository decisionLogRepository) {
        this(decisionLogRepository, eventId -> eventId);
    }

    public TraceQueryService(DecisionLogRepository decisionLogRepository, TraceIdResolver traceIdResolver) {
        this.decisionLogRepository = decisionLogRepository;
        this.traceIdResolver = traceIdResolver;
    }

    /**
     * 按事件标识查询执行链路。
     *
     * @param eventId 事件标识
     * @return 链路视图
     * @throws BizException 事件标识对应的决策记录不存在（NOT_FOUND）
     */
    public TraceView query(String eventId) {
        DecisionLog log = decisionLogRepository.findByEventId(eventId)
                .orElseThrow(() -> BizException.notFound("事件链路不存在: " + eventId));
        String traceId = traceIdResolver.resolve(eventId);
        return new TraceView(
                log.eventId(),
                traceId,
                log.finalDecision(),
                log.hitDecisions(),
                log.elapsedMs(),
                log.timeoutReason(),
                log.groupStatus());
    }
}
