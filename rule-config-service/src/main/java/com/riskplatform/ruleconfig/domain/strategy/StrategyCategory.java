package com.riskplatform.ruleconfig.domain.strategy;

/**
 * 策略类别（R3）。四类策略共表，按类别区分语义。
 *
 * <ul>
 *   <li>{@link #VERIFY} 验证策略：如短信/电话/视频核身，单规则仅允许一个，带优先级（R3.1）</li>
 *   <li>{@link #CONTROL_STATE} 状态管控：如冻结账户/终止交易（R3.2）</li>
 *   <li>{@link #CONTROL_LIMIT} 限额管控：含限额类型与阈值（R3.2）</li>
 *   <li>{@link #NOTIFY} 通知策略：如短信通知/邮件通知（R3.3）</li>
 *   <li>{@link #LISTING} 名单策略：仅记录意图，不真实写入名单库（R3.4）</li>
 * </ul>
 */
public enum StrategyCategory {
    /** 验证策略 */
    VERIFY,
    /** 状态管控 */
    CONTROL_STATE,
    /** 限额管控 */
    CONTROL_LIMIT,
    /** 通知策略 */
    NOTIFY,
    /** 名单策略 */
    LISTING
}
