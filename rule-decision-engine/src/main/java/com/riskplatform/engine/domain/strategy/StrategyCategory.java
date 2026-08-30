package com.riskplatform.engine.domain.strategy;

/**
 * 策略类别（R3.1-R3.4）。规则命中后产出的处置项分四大类，平台仅定义与记录，不真实下发。
 *
 * <ul>
 *   <li>{@link #VERIFY} 验证策略：每条规则至多一个，带整数优先级（数值越大优先级越高）。</li>
 *   <li>{@link #CONTROL_STATE} 状态管控：如冻结账户/终止交易，可零到多个。</li>
 *   <li>{@link #CONTROL_LIMIT} 限额管控：含限额类型与阈值，可零到多个。</li>
 *   <li>{@link #NOTIFY} 通知策略：如短信/邮件通知，可零到多个。</li>
 *   <li>{@link #LISTING} 名单策略：命中后应录入的目标名单，仅记录意图。</li>
 * </ul>
 */
public enum StrategyCategory {
    /** 验证策略（唯一，带优先级）。 */
    VERIFY,
    /** 状态管控策略。 */
    CONTROL_STATE,
    /** 限额管控策略。 */
    CONTROL_LIMIT,
    /** 通知策略。 */
    NOTIFY,
    /** 名单策略（仅记录意图，不真实写库）。 */
    LISTING
}
