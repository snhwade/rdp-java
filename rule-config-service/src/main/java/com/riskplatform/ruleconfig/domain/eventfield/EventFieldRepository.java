package com.riskplatform.ruleconfig.domain.eventfield;

import java.util.List;
import java.util.Optional;

/** 事件字段仓储端口（risk-console-redesign R4）。 */
public interface EventFieldRepository {

    /** 持久化新建的事件字段关联，回填主键。 */
    EventField save(EventField eventField);

    /** 更新事件字段关联（如衍生标记、用途）。 */
    EventField update(EventField eventField);

    /** 按主键删除。 */
    boolean deleteById(Long id);

    Optional<EventField> findById(Long id);

    /** 列出某事件下的全部事件字段关联（R4.1）。 */
    List<EventField> listByEvent(String eventTypeCode);

    /**
     * 同一事件下是否已关联同一字段（R4.4，精确等值）。
     *
     * <p>实现以 {@code event_type_code = ? AND field_id = ?} 精确判定，
     * 配合唯一键 {@code uk_event_field} 拒绝重复关联。
     */
    boolean existsByEventAndField(String eventTypeCode, Long fieldId);
}
