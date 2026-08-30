package com.riskplatform.ruleconfig.application.eventfield;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.application.audit.Audited;
import com.riskplatform.ruleconfig.domain.audit.AuditOpType;
import com.riskplatform.ruleconfig.domain.audit.AuditTargetType;
import com.riskplatform.ruleconfig.domain.error.RuleConfigErrorCode;
import com.riskplatform.ruleconfig.domain.eventfield.EventField;
import com.riskplatform.ruleconfig.domain.eventfield.EventFieldReferenceChecker;
import com.riskplatform.ruleconfig.domain.eventfield.EventFieldRepository;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;
import com.riskplatform.ruleconfig.domain.field.FieldDefinition;
import com.riskplatform.ruleconfig.domain.field.FieldRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事件字段应用服务（risk-console-redesign R4）。
 *
 * <p>负责事件字段关联的列表、从字段库添加、标记衍生、移除的事务编排。
 *
 * <p>校验责任划分：
 * <ul>
 *   <li>用途非空子集、事件/字段必填 → {@link EventField#create} 返回字段级错误（R4.3）</li>
 *   <li>所添加的全局字段需真实存在 → 本服务通过 {@link FieldRepository} 精确查询（R4.2）</li>
 *   <li>同事件下重复关联拒绝 → 本服务精确等值预检 + 数据库唯一键 uk_event_field（R4.4）</li>
 *   <li>被规则/评级模型引用时移除拒绝 → {@link EventFieldReferenceChecker}（R4.7，
 *       默认无引用即允许移除 R4.6）</li>
 * </ul>
 */
public class EventFieldAppService {

    private final EventFieldRepository repository;
    private final FieldRepository fieldRepository;
    private final EventFieldReferenceChecker referenceChecker;

    public EventFieldAppService(EventFieldRepository repository,
                                FieldRepository fieldRepository,
                                EventFieldReferenceChecker referenceChecker) {
        this.repository = repository;
        this.fieldRepository = fieldRepository;
        this.referenceChecker = referenceChecker;
    }

    /**
     * 列出某事件下的事件字段（R4.1），并联接字段库补充字段 code/名称/类型展示信息。
     */
    public List<EventFieldView> list(String eventTypeCode) {
        List<EventField> associations = repository.listByEvent(eventTypeCode);
        List<EventFieldView> views = new ArrayList<>(associations.size());
        for (EventField ef : associations) {
            FieldDefinition field = fieldRepository.findFieldById(ef.getFieldId()).orElse(null);
            views.add(EventFieldView.of(ef, field));
        }
        return views;
    }

    /**
     * 从字段库添加一个全局字段到某事件下（R4.2/R4.3/R4.4）。
     *
     * <p>先经聚合校验事件/字段/用途非空子集（R4.3），再校验所添加字段在字段库真实存在（R4.2），
     * 最后以精确等值预检拒绝同事件下重复关联（R4.4，配合唯一键 uk_event_field 双重保障）。
     */
    @Audited(target = AuditTargetType.EVENT_TYPE, op = AuditOpType.CREATE)
    @Transactional
    public EventFieldView add(String eventTypeCode, Long fieldId,
                              Set<EventPurpose> purposes, boolean derived) {
        EventField eventField = EventField.create(eventTypeCode, fieldId, purposes, derived); // R4.3
        FieldDefinition field = fieldRepository.findFieldById(fieldId)
                .orElseThrow(() -> new BizException(RuleConfigErrorCode.REF_NOT_FOUND,
                        "字段库中不存在该字段: id=" + fieldId)); // R4.2
        if (repository.existsByEventAndField(eventTypeCode, fieldId)) {
            throw BizException.duplicate(
                    "该字段已关联到事件: eventTypeCode=" + eventTypeCode + ", fieldId=" + fieldId); // R4.4
        }
        EventField saved = repository.save(eventField);
        return EventFieldView.of(saved, field);
    }

    /** 标记/取消事件字段的衍生字段标记（R4.5）。 */
    @Audited(target = AuditTargetType.EVENT_TYPE, op = AuditOpType.UPDATE)
    @Transactional
    public EventFieldView markDerived(Long eventFieldId, boolean derived) {
        EventField eventField = repository.findById(eventFieldId)
                .orElseThrow(() -> BizException.notFound("事件字段不存在: id=" + eventFieldId));
        eventField.markDerived(derived);
        EventField updated = repository.update(eventField);
        FieldDefinition field = fieldRepository.findFieldById(updated.getFieldId()).orElse(null);
        return EventFieldView.of(updated, field);
    }

    /**
     * 移除事件字段（R4.6/R4.7）。
     *
     * <p>移除前由 {@link EventFieldReferenceChecker} 检查该事件下规则或评级模型是否仍引用；
     * 存在引用则拒绝移除并返回 {@code EVENT_FIELD.IN_USE}（保留关联，R4.7）；无引用即移除（R4.6）。
     */
    @Audited(target = AuditTargetType.EVENT_TYPE, op = AuditOpType.DELETE)
    @Transactional
    public void remove(Long eventFieldId) {
        EventField eventField = repository.findById(eventFieldId)
                .orElseThrow(() -> BizException.notFound("事件字段不存在: id=" + eventFieldId));
        List<String> references = referenceChecker.findReferences(eventField);
        if (references != null && !references.isEmpty()) {
            throw new BizException(RuleConfigErrorCode.EVENT_FIELD_IN_USE,
                    "事件字段仍被引用，无法移除: " + String.join("、", references)); // R4.7
        }
        repository.deleteById(eventFieldId);
    }

    /**
     * 事件字段视图（列表展示用），联接字段库字段 code/名称/数据类型。
     *
     * <p>字段库记录缺失（脏数据）时 code/name/dataType 为 {@code null}，不阻断列表展示。
     */
    public record EventFieldView(Long id, String eventTypeCode, Long fieldId,
                                 String fieldCode, String fieldName, String dataType,
                                 List<String> purposes, boolean derived) {

        static EventFieldView of(EventField ef, FieldDefinition field) {
            return new EventFieldView(
                    ef.getId(), ef.getEventTypeCode(), ef.getFieldId(),
                    field == null ? null : field.code(),
                    field == null ? null : field.name(),
                    field == null ? null : field.dataType(),
                    ef.getPurposes().stream().map(Enum::name).toList(),
                    ef.isDerived());
        }
    }
}
