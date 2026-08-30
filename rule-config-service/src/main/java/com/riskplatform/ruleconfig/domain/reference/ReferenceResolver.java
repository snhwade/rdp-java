package com.riskplatform.ruleconfig.domain.reference;

/**
 * 参数管理对象存在性解析端口（risk-console-redesign R14.1/R14.2，任务 2.3）。
 *
 * <p>{@link ReferenceValidator} 通过该端口判定规则/决策流/评级模型所引用的事件与事件字段
 * 是否在参数管理中真实存在。事件存在性以事件 code 解析（复用 eventtype 子域）；事件字段
 * 存在性以「事件 code + 字段 code」解析（事件字段子域由任务 4 落地，本期提供默认实现作为
 * 扩展点）。
 */
public interface ReferenceResolver {

    /**
     * 指定事件 code 是否在参数管理中存在。
     *
     * @param eventCode 事件 code
     * @return 存在返回 {@code true}
     */
    boolean eventExists(String eventCode);

    /**
     * 指定事件下是否存在指定字段 code 的事件字段关联。
     *
     * <p>事件字段子域尚未落地时（任务 4 之前），默认实现返回 {@code false}，由
     * {@link ReferenceValidator} 据此拒绝事件字段引用。任务 4 落地后由真实事件字段
     * 仓储实现替换。
     *
     * @param eventCode 事件 code
     * @param fieldCode 事件字段 code
     * @return 存在返回 {@code true}
     */
    boolean eventFieldExists(String eventCode, String fieldCode);
}
