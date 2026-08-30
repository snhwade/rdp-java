package com.riskplatform.engine.domain.decisionflow.node;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.domain.list.ListCheckPort;
import com.riskplatform.engine.domain.rule.HitDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 名单检查节点（LIST_CHECK，enhancement-plan T4）。
 *
 * <p>优先经 {@link ListCheckPort} 调用筛查服务；服务不可用或未命中时，回退上下文
 * {@code blackHit}/{@code watchHit}（兼容网关预注入），并打 warn。
 */
public final class ListCheckNodeHandler implements NodeHandler {

    private static final Logger log = LoggerFactory.getLogger(ListCheckNodeHandler.class);

    private final ListCheckPort listCheckPort;

    public ListCheckNodeHandler(ListCheckPort listCheckPort) {
        this.listCheckPort = listCheckPort;
    }

    /** 兼容旧测试：无端口时仅读上下文。 */
    public ListCheckNodeHandler() {
        this(null);
    }

    @Override
    public DecisionFlowDef.NodeType supportedType() {
        return DecisionFlowDef.NodeType.LIST_CHECK;
    }

    @Override
    public NodeResult handle(DecisionFlowDef.Node node, FlowContext ctx) {
        ListCheckPort.ListHit hit = resolveHit(ctx);

        Map<String, Object> assignments = new HashMap<>();
        assignments.put("blackHit", hit.blackHit());
        assignments.put("watchHit", hit.watchHit());
        assignments.put("whiteHit", hit.whiteHit());
        assignments.put("listCheckFromService", hit.fromService());

        // 写回 env，供后续节点使用
        ctx.env().put("blackHit", hit.blackHit());
        ctx.env().put("watchHit", hit.watchHit());
        ctx.env().put("whiteHit", hit.whiteHit());

        if (hit.blackHit()) {
            HitDecision reject = new HitDecision(-9000L, 1, Decision.REJECT);
            assignments.put("lastDecision", Decision.REJECT.name());
            return new NodeResult(List.of(reject), assignments);
        }
        if (hit.watchHit()) {
            HitDecision review = new HitDecision(-8000L, 10, Decision.REVIEW);
            assignments.put("lastDecision", Decision.REVIEW.name());
            return new NodeResult(List.of(review), assignments);
        }
        return new NodeResult(List.of(), assignments);
    }

    private ListCheckPort.ListHit resolveHit(FlowContext ctx) {
        if (listCheckPort != null) {
            try {
                ListCheckPort.ListHit fromService = listCheckPort.check(ctx.env());
                if (fromService.fromService()) {
                    return fromService;
                }
            } catch (Exception ex) {
                log.warn("LIST_CHECK 服务调用异常，回退上下文注入: {}", ex.getMessage());
            }
        }

        boolean black = isTrue(ctx.env().get("blackHit"));
        boolean watch = isTrue(ctx.env().get("watchHit"));
        if (black || watch) {
            log.warn("LIST_CHECK 使用上下文预注入 blackHit={} watchHit={}（非服务实调用）", black, watch);
        }
        return ListCheckPort.ListHit.fromContext(black, watch);
    }

    private static boolean isTrue(Object flag) {
        return Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag));
    }
}
