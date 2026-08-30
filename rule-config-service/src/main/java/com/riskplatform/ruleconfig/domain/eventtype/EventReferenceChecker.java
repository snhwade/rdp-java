package com.riskplatform.ruleconfig.domain.eventtype;

import java.util.List;

/**
 * 事件引用/依赖检查领域服务端口（risk-console-redesign R2.9 / R14.2）。
 *
 * <p>删除事件前，由该服务检查事件下是否仍存在关联的事件字段、规则包、决策流或评级模型；
 * 存在则拒绝删除（{@code EVENT.HAS_DEPENDENCY}，Property 6）。
 *
 * <p>本期任务 2.2 仅定义端口并在删除链路接入；真正跨子域的依赖扫描实现由任务 2.3
 * 提供并替换默认实现。默认实现 {@link #noop()} 视事件无任何依赖（便于先打通 CRUD）。
 */
public interface EventReferenceChecker {

    /**
     * 返回指定事件存在的依赖类型描述列表（如「事件字段」「规则包」）。
     *
     * @param eventCode 事件 code
     * @return 依赖描述列表；为空表示无依赖，可安全删除
     */
    List<String> findDependencies(String eventCode);

    /** 默认无依赖实现（占位，任务 2.3 将提供真实跨子域扫描实现）。 */
    static EventReferenceChecker noop() {
        return eventCode -> List.of();
    }
}
