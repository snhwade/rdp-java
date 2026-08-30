package com.riskplatform.ruleconfig.domain.eventfield;

import java.util.ArrayList;
import java.util.List;

/**
 * 事件字段引用检查领域服务（risk-console-redesign R4.7）。
 *
 * <p>聚合若干 {@link EventFieldReferenceSource}（该事件下的规则 / 评级模型），移除事件字段前
 * 依次询问各来源是否存在引用，返回存在引用的类型描述列表。返回非空即表示事件字段不可移除，
 * 由 {@code EventFieldAppService} 据此拒绝并返回 {@code EVENT_FIELD.IN_USE}（Property 12）。
 *
 * <p>本类为纯领域对象（不依赖 Spring），来源集合由基础设施层装配后注入，便于以内存假体进行
 * 单元/属性测试。来源集合为空时视为无引用，允许移除（R4.6）。
 */
public class CompositeEventFieldReferenceChecker implements EventFieldReferenceChecker {

    private final List<EventFieldReferenceSource> sources;

    public CompositeEventFieldReferenceChecker(List<EventFieldReferenceSource> sources) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    @Override
    public List<String> findReferences(EventField eventField) {
        if (eventField == null) {
            return List.of();
        }
        List<String> references = new ArrayList<>();
        for (EventFieldReferenceSource source : sources) {
            if (source.isReferenced(eventField)) {
                references.add(source.referenceType());
            }
        }
        return references;
    }
}
