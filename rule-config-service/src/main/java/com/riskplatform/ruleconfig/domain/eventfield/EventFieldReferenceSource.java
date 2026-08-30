package com.riskplatform.ruleconfig.domain.eventfield;

/**
 * 事件字段引用来源端口（risk-console-redesign R4.7）。
 *
 * <p>每个实现代表一类可能引用某事件字段的资源（该事件下的规则 / 评级模型），
 * 由 {@link CompositeEventFieldReferenceChecker} 聚合，移除事件字段前逐一询问是否存在引用。
 *
 * <p>这是一个开放扩展点：规则（任务 8）、评级模型（任务 14）落地后新增或替换对应来源实现，
 * 无需改动移除链路与聚合检查器。
 */
public interface EventFieldReferenceSource {

    /** 该来源对应的引用类型中文描述（如「规则」「评级模型」），用于拼装拒绝原因。 */
    String referenceType();

    /**
     * 指定事件字段是否被该类资源引用。
     *
     * <p>实现应在底层资源（如表）尚不存在时优雅降级返回 {@code false}，
     * 不得因可选资源缺失而中断移除前检查（R4.6）。
     *
     * @param eventField 待移除的事件字段
     * @return 存在引用返回 {@code true}
     */
    boolean isReferenced(EventField eventField);
}
