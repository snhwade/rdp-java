package com.riskplatform.engine.domain.strategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略聚合器（R1.2 / R1.3 / R3.5 / R3.6 / R3.7）。
 *
 * <p>纯函数 / 无状态组件：仅依据入参产出聚合结果，便于属性测试（Property 2 置换不变）。
 * 平台只定义并记录输出了哪些策略，不真实下发。
 *
 * <p><b>命中模式聚合规则 {@link #aggregateHit}（R1.2）：</b>
 * <ul>
 *   <li>验证策略 VERIFY：取所有命中规则验证策略中 priority 最大者（唯一输出）。</li>
 *   <li>状态管控 CONTROL_STATE：取最高优先级；若同优先级多个则全部输出。</li>
 *   <li>限额管控 CONTROL_LIMIT：全部输出，同一限额类型取阈值最小者。</li>
 *   <li>通知策略 NOTIFY：全部输出（按编码去重）。</li>
 *   <li>名单策略 LISTING：全部输出（按编码去重，仅记录）。</li>
 * </ul>
 *
 * <p><b>评分模式映射 {@link #aggregateScore}（R1.3 / R4.4）：</b>定位总分落入的区间
 * （开闭由区间标志确定），输出该区间绑定的策略与风险等级。
 *
 * <p>所有输出均按确定性顺序排序，保证与命中规则输入顺序无关（置换不变）。
 */
public class StrategyAggregator {

    /** strategyCode 排序比较器（null 安全），用于产出确定性顺序。 */
    private static final Comparator<StrategyItem> BY_CODE =
            Comparator.comparing(s -> s.strategyCode() == null ? "" : s.strategyCode());

    /**
     * 命中模式策略聚合（R1.2 / R3.5 / R3.6 / R3.7）。
     *
     * @param hitRules 命中规则及其绑定策略列表（可空）
     * @return 聚合结果（命中模式无风险等级，riskLevelCode 为 null）；无任何策略的类别被省略（R3.7）
     */
    public StrategyAggregateResult aggregateHit(List<HitRuleStrategies> hitRules) {
        List<StrategyItem> all = flatten(hitRules);

        List<StrategyItem> output = new ArrayList<>();
        output.addAll(aggregateVerify(all));        // 唯一最高优先级
        output.addAll(aggregateControlState(all));  // 最高优先级，同级全输出
        output.addAll(aggregateControlLimit(all));  // 全输出，同类型取阈值最小
        output.addAll(dedupByCode(all, StrategyCategory.NOTIFY));   // 全输出去重
        output.addAll(dedupByCode(all, StrategyCategory.LISTING));  // 全输出去重（仅记录）

        return new StrategyAggregateResult(output, null);
    }

    /**
     * 评分模式按总分映射区间，输出区间策略与风险等级（R1.3 / R4.4）。
     *
     * @param totalScore 各触发规则累加得到的总分
     * @param scoreBands 规则包配置的分值区间（不重叠）
     * @return 命中区间的策略 + 风险等级；未命中任何区间时返回空策略且风险等级为 null
     */
    public StrategyAggregateResult aggregateScore(BigDecimal totalScore, List<ScoreBand> scoreBands) {
        if (totalScore == null || scoreBands == null) {
            return new StrategyAggregateResult(List.of(), null);
        }
        for (ScoreBand band : scoreBands) {
            if (band != null && band.contains(totalScore)) {
                List<StrategyItem> strategies =
                        band.strategies() == null ? List.of() : List.copyOf(band.strategies());
                return new StrategyAggregateResult(strategies, band.riskLevelCode());
            }
        }
        return new StrategyAggregateResult(List.of(), null);
    }

    /** 展开所有命中规则的策略为单一列表。 */
    private List<StrategyItem> flatten(List<HitRuleStrategies> hitRules) {
        List<StrategyItem> all = new ArrayList<>();
        if (hitRules == null) {
            return all;
        }
        for (HitRuleStrategies hr : hitRules) {
            if (hr == null || hr.strategies() == null) {
                continue;
            }
            for (StrategyItem item : hr.strategies()) {
                if (item != null) {
                    all.add(item);
                }
            }
        }
        return all;
    }

    /** 验证策略：取 priority 最大者，唯一输出；priority 相同则按 strategyCode 取最小，保证确定性。 */
    private List<StrategyItem> aggregateVerify(List<StrategyItem> all) {
        return all.stream()
                .filter(s -> s.category() == StrategyCategory.VERIFY)
                .max(Comparator.comparingInt(StrategyItem::priority)
                        .thenComparing(BY_CODE.reversed()))
                .map(List::of)
                .orElseGet(List::of);
    }

    /** 状态管控：取最高优先级；同优先级多个则全部输出（按编码去重并排序）。 */
    private List<StrategyItem> aggregateControlState(List<StrategyItem> all) {
        List<StrategyItem> states = all.stream()
                .filter(s -> s.category() == StrategyCategory.CONTROL_STATE)
                .toList();
        if (states.isEmpty()) {
            return List.of();
        }
        int maxPriority = states.stream().mapToInt(StrategyItem::priority).max().orElseThrow();
        Map<String, StrategyItem> byCode = new LinkedHashMap<>();
        states.stream()
                .filter(s -> s.priority() == maxPriority)
                .forEach(s -> byCode.putIfAbsent(s.strategyCode(), s));
        return byCode.values().stream().sorted(BY_CODE).toList();
    }

    /** 限额管控：全部输出，同一限额类型取阈值最小者；按限额类型排序保证确定性。 */
    private List<StrategyItem> aggregateControlLimit(List<StrategyItem> all) {
        Map<String, StrategyItem> byType = new LinkedHashMap<>();
        for (StrategyItem s : all) {
            if (s.category() != StrategyCategory.CONTROL_LIMIT) {
                continue;
            }
            String type = s.limitType();
            StrategyItem existing = byType.get(type);
            if (existing == null || isSmallerThreshold(s, existing)) {
                byType.put(type, s);
            }
        }
        return byType.values().stream()
                .sorted(Comparator.comparing(s -> s.limitType() == null ? "" : s.limitType()))
                .toList();
    }

    /** 比较两条限额策略，s 的阈值是否更小（null 阈值视为最大，不优先）。 */
    private boolean isSmallerThreshold(StrategyItem candidate, StrategyItem existing) {
        BigDecimal c = candidate.threshold();
        BigDecimal e = existing.threshold();
        if (c == null) {
            return false;
        }
        if (e == null) {
            return true;
        }
        return c.compareTo(e) < 0;
    }

    /** 指定类别全部输出，按 strategyCode 去重并排序。 */
    private List<StrategyItem> dedupByCode(List<StrategyItem> all, StrategyCategory category) {
        Map<String, StrategyItem> byCode = new LinkedHashMap<>();
        all.stream()
                .filter(s -> s.category() == category)
                .forEach(s -> byCode.putIfAbsent(s.strategyCode(), s));
        return byCode.values().stream().sorted(BY_CODE).toList();
    }
}
