package com.riskplatform.engine.application;

import com.riskplatform.engine.domain.strategy.StrategyAggregateResult;
import com.riskplatform.engine.domain.strategy.StrategyItem;
import com.riskplatform.engine.domain.strategy.output.DecisionStrategyOutputRepository;
import com.riskplatform.engine.domain.strategy.output.DecisionStrategyOutputView;
import com.riskplatform.engine.domain.strategy.output.DecisionStrategyRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策产出策略记录应用服务（R3.4/R3.5）。
 *
 * <p>职责：把一次决策（命中模式聚合 / 评分区间映射）产出的策略列表落库到
 * decision_strategy_output，并构造随决策响应返回的视图。
 *
 * <p><b>只记录不下发</b>：本服务<b>不调用任何外部系统</b>（短信/账户/邮件/名单库均不触发），
 * 名单策略（LISTING）同样仅记录意图。返回视图的 dispatched 恒为 false。
 *
 * <p>接线点说明：主决策编排（后续 6.2 RulePackageExecutor 产出策略、9.x 编排串联）
 * 在得到 {@link StrategyAggregateResult} 后调用 {@link #record} 即可完成记录与返回；
 * 本服务为「记录组件」，已就位且可被注入调用。
 */
@Service
public class DecisionStrategyOutputService {

    private final DecisionStrategyOutputRepository repository;

    public DecisionStrategyOutputService(DecisionStrategyOutputRepository repository) {
        this.repository = repository;
    }

    /**
     * 记录一次决策产出的策略列表并返回响应视图。
     *
     * @param eventId    事件标识
     * @param decisionId 关联决策日志 ID（可为 null）
     * @param result     策略聚合结果（命中模式 / 评分模式区间映射，来自 StrategyAggregator）
     * @return 随决策响应返回的策略产出视图（dispatched 恒为 false）
     */
    public DecisionStrategyOutputView record(String eventId,
                                             Long decisionId,
                                             StrategyAggregateResult result) {
        List<StrategyItem> strategies =
                (result == null || result.strategies() == null) ? List.of() : result.strategies();
        return record(eventId, decisionId, null, strategies);
    }

    /**
     * 记录一次决策产出的策略列表并返回响应视图（可指定命中规则归属）。
     *
     * @param eventId    事件标识
     * @param decisionId 关联决策日志 ID（可为 null）
     * @param ruleV2Id   命中规则 ID；为 null 表示评分区间映射产出
     * @param strategies 待记录的策略项列表
     * @return 随决策响应返回的策略产出视图（dispatched 恒为 false）
     */
    public DecisionStrategyOutputView record(String eventId,
                                             Long decisionId,
                                             Long ruleV2Id,
                                             List<StrategyItem> strategies) {
        List<StrategyItem> safe = strategies == null ? List.of() : strategies;

        List<DecisionStrategyRecord> records = new ArrayList<>(safe.size());
        List<DecisionStrategyOutputView.Item> items = new ArrayList<>(safe.size());
        for (StrategyItem item : safe) {
            if (item == null) {
                continue;
            }
            Map<String, Object> payload = toPayload(item);
            records.add(DecisionStrategyRecord.pending(
                    eventId, decisionId, ruleV2Id, item.category(), item.strategyCode(), payload));
            items.add(new DecisionStrategyOutputView.Item(
                    item.category(), item.strategyCode(), ruleV2Id, payload));
        }

        // 只记录：落库，不下发任何外部系统
        repository.saveAll(records);

        // dispatched 恒为 false，明确表达「只记录不下发」边界
        return new DecisionStrategyOutputView(eventId, decisionId, items, false);
    }

    /** 查询某次事件已记录的策略产出（随响应返回 / 链路查询用）。 */
    public DecisionStrategyOutputView viewByEventId(String eventId) {
        List<DecisionStrategyRecord> records = repository.findByEventId(eventId);
        Long decisionId = records.isEmpty() ? null : records.get(0).decisionId();
        List<DecisionStrategyOutputView.Item> items = records.stream()
                .map(r -> new DecisionStrategyOutputView.Item(
                        r.category(), r.strategyCode(), r.ruleV2Id(), r.payload()))
                .toList();
        return new DecisionStrategyOutputView(eventId, decisionId, items, false);
    }

    /**
     * 把策略项的附加属性整理为 payload（只记录这些意图，不据此下发）。
     * 合并 limitType/threshold/priority 等结构化字段与自定义 params。
     */
    private Map<String, Object> toPayload(StrategyItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (item.priority() != 0) {
            payload.put("priority", item.priority());
        }
        if (item.limitType() != null) {
            payload.put("limitType", item.limitType());
        }
        if (item.threshold() != null) {
            payload.put("threshold", item.threshold());
        }
        if (item.params() != null && !item.params().isEmpty()) {
            payload.putAll(item.params());
        }
        return payload;
    }
}
