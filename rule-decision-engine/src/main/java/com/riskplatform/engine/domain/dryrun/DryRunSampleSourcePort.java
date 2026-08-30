package com.riskplatform.engine.domain.dryrun;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 试运行样本来源端口（R5.1/R5.2）。
 *
 * <p>领域层定义、基础设施层实现：按来源（订单/事件）、时间范围与数量上限拉取历史样本，
 * 转为「影子上下文」{@link DryRunSample}。只读历史数据，不触发任何在线决策。
 */
public interface DryRunSampleSourcePort {

    /**
     * 拉取历史样本。
     *
     * @param source   样本来源（ORDER/EVENT）
     * @param from     样本数据起始时间（可空，表示不限下界）
     * @param to       样本数据结束时间（可空，表示不限上界）
     * @param limit    样本数量上限（&lt;=0 表示不限，由实现侧设安全上限）
     * @return 影子样本列表（按事件时间倒序）；无样本返回空列表
     */
    List<DryRunSample> fetch(DryRunSampleSource source, LocalDateTime from, LocalDateTime to, int limit);
}
