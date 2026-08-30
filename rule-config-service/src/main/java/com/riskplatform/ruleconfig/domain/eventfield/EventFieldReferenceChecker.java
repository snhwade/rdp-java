package com.riskplatform.ruleconfig.domain.eventfield;

import java.util.List;

/**
 * 事件字段引用检查领域服务端口（risk-console-redesign R4.7）。
 *
 * <p>移除事件字段前，由该服务检查该事件字段是否仍被该事件下的规则或评级模型引用；
 * 存在引用则拒绝移除（{@code EVENT_FIELD.IN_USE}，Property 12）。
 *
 * <p>这是一个开放扩展点：规则（任务 8）与评级模型（任务 14）落地后可提供更精确的引用
 * 来源实现而无需改动移除链路。默认实现 {@link #noop()} 视事件字段无任何引用，
 * 此时允许移除（R4.6）。
 */
public interface EventFieldReferenceChecker {

    /**
     * 返回引用指定事件字段的引用类型描述列表（如「规则」「评级模型」）。
     *
     * @param eventField 待移除的事件字段
     * @return 引用描述列表；为空表示无引用，可安全移除（R4.6）
     */
    List<String> findReferences(EventField eventField);

    /** 默认无引用实现（占位扩展点，R4.6）。 */
    static EventFieldReferenceChecker noop() {
        return eventField -> List.of();
    }
}
