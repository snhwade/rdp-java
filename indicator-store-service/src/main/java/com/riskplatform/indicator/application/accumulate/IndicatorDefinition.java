package com.riskplatform.indicator.application.accumulate;

import com.riskplatform.indicator.domain.SliceGranularity;

import java.util.List;

/**
 * 指标累计定义（消费侧，R8.2/R8.5）。
 *
 * <p>描述一个需要从订单终态流中累计的指标：引用名、绑定事件、统计维度、切片粒度、
 * 时间窗口、累计脚本。仅上线（online=true）且订单事件匹配的指标才会累计。
 *
 * @param refName              指标引用名（与读取路径 refName 一致）
 * @param eventTypeCodes       绑定的事件类型 code 列表
 * @param dimensions           统计维度字段（订单 fields 中需全部存在）
 * @param granularity          切片粒度（MINUTE/HOUR/DAY）
 * @param windowDays           时间窗口天数（用于 TTL 老化）
 * @param accScript            Aviator 累计脚本（变量 current 为切片当前值，可引用订单字段）
 * @param online               是否上线（仅上线指标参与累计）
 */
public record IndicatorDefinition(
        String refName,
        List<String> eventTypeCodes,
        List<String> dimensions,
        SliceGranularity granularity,
        int windowDays,
        String accScript,
        boolean online) {

    /** 切片 TTL（秒）：窗口秒数 + 一个切片宽度冗余，保证窗口内切片不被提前老化。 */
    public long ttlSeconds() {
        long windowSeconds = (long) windowDays * 86400L;
        return windowSeconds + granularity.stepSeconds();
    }

    /** 订单事件是否匹配本指标绑定的事件列表。 */
    public boolean matchesEvent(String eventTypeCode) {
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            return false;
        }
        return eventTypeCodes != null && eventTypeCodes.contains(eventTypeCode);
    }
}
