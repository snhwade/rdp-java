package com.riskplatform.ruleconfig.application.eventfield;

import com.riskplatform.ruleconfig.domain.eventfield.EventField;
import com.riskplatform.ruleconfig.domain.eventfield.EventFieldRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版事件字段仓储，用于事件字段应用服务的单元/属性测试。
 *
 * <p>关键点：{@link #existsByEventAndField} 以<strong>精确等值</strong>实现，模拟数据库唯一键
 * {@code uk_event_field}(event_type_code, field_id) + 精确等值查询语义（R4.4），不做前缀/模糊匹配；
 * {@link #save} 在精确重复时抛出，模拟唯一键约束。
 */
public class InMemoryEventFieldRepository implements EventFieldRepository {

    private final Map<Long, EventField> store = new LinkedHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public EventField save(EventField eventField) {
        if (existsByEventAndField(eventField.getEventTypeCode(), eventField.getFieldId())) {
            throw new IllegalStateException("duplicate event_field (unique key): "
                    + eventField.getEventTypeCode() + "/" + eventField.getFieldId());
        }
        long id = seq.incrementAndGet();
        eventField.assignId(id);
        store.put(id, eventField);
        return eventField;
    }

    @Override
    public EventField update(EventField eventField) {
        store.put(eventField.getId(), eventField);
        return eventField;
    }

    @Override
    public boolean deleteById(Long id) {
        return store.remove(id) != null;
    }

    @Override
    public Optional<EventField> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<EventField> listByEvent(String eventTypeCode) {
        List<EventField> result = new ArrayList<>();
        for (EventField ef : store.values()) {
            if (eventTypeCode != null && eventTypeCode.equals(ef.getEventTypeCode())) {
                result.add(ef);
            }
        }
        return result;
    }

    @Override
    public boolean existsByEventAndField(String eventTypeCode, Long fieldId) {
        if (eventTypeCode == null || fieldId == null) {
            return false;
        }
        return store.values().stream().anyMatch(ef ->
                eventTypeCode.equals(ef.getEventTypeCode()) && fieldId.equals(ef.getFieldId()));
    }
}
