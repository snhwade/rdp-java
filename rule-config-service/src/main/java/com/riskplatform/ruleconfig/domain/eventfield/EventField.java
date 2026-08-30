package com.riskplatform.ruleconfig.domain.eventfield;

import com.riskplatform.common.error.ValidationException;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 事件字段聚合根（risk-console-redesign R4）。
 *
 * <p>事件字段是「事件—全局字段」多对多关联：把字段库中的全局字段添加到某个具体事件下，
 * 标注用途（计算/决策）并可标记是否为衍生字段。
 *
 * <p>不变式：
 * <ul>
 *   <li>eventTypeCode 必填（关联的事件 code，R4.2）</li>
 *   <li>fieldId 必填（关联的字段库 field_library.id，R4.2）</li>
 *   <li>purposes 为 {COMPUTE, DECISION} 的非空子集（R4.3，Property 4 同语义）</li>
 *   <li>derived 衍生字段标记（R4.5）</li>
 * </ul>
 *
 * <p>本类为纯领域对象，不依赖框架。校验在 {@link #create} 中完成，违反时抛出
 * {@link ValidationException}（聚合字段级错误）。
 */
public class EventField {

    private Long id;
    private String eventTypeCode;
    private Long fieldId;
    /** 事件字段用途多选（R4.3，非空子集）。保持插入顺序。 */
    private final Set<EventPurpose> purposes = new LinkedHashSet<>();
    /** 是否衍生字段（R4.5）。 */
    private boolean derived;

    private EventField() {
    }

    /**
     * 工厂方法（R4.2/R4.3/R4.5）：创建一个事件字段关联，校验事件、字段、用途非空子集。
     */
    public static EventField create(String eventTypeCode, Long fieldId,
                                    Set<EventPurpose> purposes, boolean derived) {
        EventField ef = new EventField();
        ef.eventTypeCode = eventTypeCode;
        ef.fieldId = fieldId;
        ef.derived = derived;
        if (purposes != null) {
            ef.purposes.addAll(purposes);
        }
        ef.validate();
        return ef;
    }

    /** 从持久化重建（不重复校验）。 */
    public static EventField rehydrate(Long id, String eventTypeCode, Long fieldId,
                                       Set<EventPurpose> purposes, boolean derived) {
        EventField ef = new EventField();
        ef.id = id;
        ef.eventTypeCode = eventTypeCode;
        ef.fieldId = fieldId;
        ef.derived = derived;
        if (purposes != null) {
            ef.purposes.addAll(purposes);
        }
        return ef;
    }

    /** 校验不变式，违反时抛出聚合字段错误（R4.2/R4.3）。 */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            errors.field("eventTypeCode", "必填");
        }
        if (fieldId == null) {
            errors.field("fieldId", "必填");
        }
        if (purposes.isEmpty()) {
            errors.field("purposes", "至少选择一个事件字段用途（计算/决策）");
        }
        errors.throwIfAny();
    }

    /** 标记/取消衍生字段标记（R4.5）。 */
    public void markDerived(boolean derived) {
        this.derived = derived;
    }

    public Long getId() {
        return id;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public boolean isDerived() {
        return derived;
    }

    /** 返回不可变的事件字段用途集合。 */
    public Set<EventPurpose> getPurposes() {
        return Collections.unmodifiableSet(purposes.isEmpty()
                ? EnumSet.noneOf(EventPurpose.class) : EnumSet.copyOf(purposes));
    }
}
