package com.riskplatform.engine.domain.dryrun;

import java.util.Map;

/**
 * 试运行样本（影子上下文，R5.2）。
 *
 * <p>一条历史样本（订单/事件）经反序列化得到的「影子上下文」：仅用于离线空跑评估，
 * 不参与任何在线决策链路。{@link #context} 即逐条评估时注入规则/规则包执行器的上下文视图。
 *
 * @param sampleId      样本标识（订单事件 eventId，用于命中明细下钻）
 * @param eventTypeCode 事件类型编码（可空）
 * @param context       影子决策上下文（事件字段；指标值由评估时按需补充）
 */
public record DryRunSample(String sampleId, String eventTypeCode, Map<String, Object> context) {

    public DryRunSample {
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
