package com.riskplatform.engine.domain.dryrun;

/**
 * 试运行样本来源（R5.1）。
 *
 * <ul>
 *   <li>{@link #ORDER} 历史订单样本：从 MySQL {@code risk_order} 读取已受理订单的上下文逐条空跑。</li>
 *   <li>{@link #EVENT} 事件样本集：预留扩展。当前阶段事件样本暂以订单样本承载
 *       （订单即一次决策事件的落库形态，eventId/eventTypeCode/context 一致），
 *       后续若引入独立事件样本存储再在此分流。</li>
 * </ul>
 */
public enum DryRunSampleSource {
    /** 历史订单样本（risk_order）。 */
    ORDER,
    /** 事件样本集（预留扩展，当前以订单样本承载）。 */
    EVENT
}
