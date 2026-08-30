package com.riskplatform.ruleconfig.domain.eventtype;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨子域事件依赖检查领域服务（risk-console-redesign R2.9，任务 2.3）。
 *
 * <p>聚合若干 {@link EventDependencySource}（事件字段 / 规则包 / 决策流 / 评级模型），
 * 删除事件前依次询问各来源是否存在依赖，返回存在依赖的类型描述列表。返回非空即表示
 * 事件不可删除，由 {@code EventTypeAppService} 据此拒绝并返回 {@code EVENT.HAS_DEPENDENCY}
 * （Property 6）。
 *
 * <p>本类为纯领域对象（不依赖 Spring），来源集合由基础设施层装配后注入，便于以内存
 * 假体进行单元/属性测试。
 */
public class CompositeEventReferenceChecker implements EventReferenceChecker {

    private final List<EventDependencySource> sources;

    public CompositeEventReferenceChecker(List<EventDependencySource> sources) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    @Override
    public List<String> findDependencies(String eventCode) {
        if (eventCode == null || eventCode.isBlank()) {
            return List.of();
        }
        List<String> dependencies = new ArrayList<>();
        for (EventDependencySource source : sources) {
            if (source.hasDependency(eventCode)) {
                dependencies.add(source.dependencyType());
            }
        }
        return dependencies;
    }
}
