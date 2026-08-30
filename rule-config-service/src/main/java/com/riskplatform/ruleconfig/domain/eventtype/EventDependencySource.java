package com.riskplatform.ruleconfig.domain.eventtype;

/**
 * 事件依赖来源端口（risk-console-redesign R2.9，任务 2.3）。
 *
 * <p>每个实现代表一类可能依赖某事件的资源（事件字段 / 规则包 / 决策流 / 评级模型），
 * 由 {@link CompositeEventReferenceChecker} 聚合，删除事件前逐一询问是否存在依赖。
 *
 * <p>这是一个开放扩展点：后续模块（任务 4 事件字段、任务 8 规则包、任务 11 决策流、
 * 任务 14 评级模型）若需要更精确的依赖判定，可新增或替换对应的来源实现而无需改动
 * 删除链路与聚合检查器。
 */
public interface EventDependencySource {

    /** 该来源对应的依赖类型中文描述（如「事件字段」「规则包」），用于拼装拒绝原因。 */
    String dependencyType();

    /**
     * 指定事件是否存在该类依赖。
     *
     * <p>实现应在底层资源（如表）尚不存在时优雅降级返回 {@code false}，
     * 不得因可选资源缺失而中断整个删除前检查。
     *
     * @param eventCode 事件 code
     * @return 存在依赖返回 {@code true}
     */
    boolean hasDependency(String eventCode);
}
