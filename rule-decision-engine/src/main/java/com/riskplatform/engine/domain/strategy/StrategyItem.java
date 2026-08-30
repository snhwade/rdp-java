package com.riskplatform.engine.domain.strategy;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 引擎侧轻量策略 DTO（无状态，便于属性测试）。
 *
 * <p>表示一条规则绑定的某一策略，聚合时按 {@link #category} 走不同归并规则。
 *
 * @param category    策略类别
 * @param strategyCode 策略编码（同编码视为同一策略，去重依据）
 * @param priority    优先级（仅验证/状态管控使用，数值越大优先级越高；其余可填 0）
 * @param limitType   限额类型（仅限额管控使用，同一类型取阈值最小者；其余为 null）
 * @param threshold   限额阈值（仅限额管控使用；其余为 null）
 * @param params      策略附加参数（如通知渠道/验证方式/目标名单等，只记录不下发）
 */
public record StrategyItem(StrategyCategory category,
                           String strategyCode,
                           int priority,
                           String limitType,
                           BigDecimal threshold,
                           Map<String, Object> params) {
}
