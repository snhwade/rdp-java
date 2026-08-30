package com.riskplatform.ruleconfig.infrastructure.eventfield;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.eventfield.EventField;
import com.riskplatform.ruleconfig.domain.eventfield.EventFieldRepository;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;
import org.springframework.stereotype.Repository;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 事件字段仓储 MyBatis-Plus 实现（risk-console-redesign R4.8）。
 *
 * <p>purposes 以 JSON 数组字符串持久化到 {@code purposes_json} 列（如
 * {@code ["COMPUTE","DECISION"]}），使用共享的 {@link ObjectMapper} 手动序列化/反序列化为
 * {@link EventPurpose} 集合；derived 衍生标记以 0/1 存储。
 *
 * <p>{@link #existsByEventAndField} 以 {@code event_type_code = ? AND field_id = ?} 精确等值
 * 判定，配合唯一键 {@code uk_event_field} 拒绝同一事件下重复关联（R4.4），禁止前缀/模糊匹配。
 */
@Repository
public class EventFieldRepositoryImpl implements EventFieldRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final EventFieldMapper mapper;
    private final ObjectMapper objectMapper;

    public EventFieldRepositoryImpl(EventFieldMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public EventField save(EventField eventField) {
        EventFieldPO po = toPO(eventField);
        mapper.insert(po);
        eventField.assignId(po.getId());
        return eventField;
    }

    @Override
    public EventField update(EventField eventField) {
        mapper.updateById(toPO(eventField));
        return eventField;
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public Optional<EventField> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<EventField> listByEvent(String eventTypeCode) {
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<EventFieldPO>()
                        .eq(EventFieldPO::getEventTypeCode, eventTypeCode))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByEventAndField(String eventTypeCode, Long fieldId) {
        if (eventTypeCode == null || eventTypeCode.isBlank() || fieldId == null) {
            return false;
        }
        // 精确等值存在性判定（R4.4），配合唯一键 uk_event_field 双重保障。
        return mapper.exists(new LambdaQueryWrapper<EventFieldPO>()
                .eq(EventFieldPO::getEventTypeCode, eventTypeCode)
                .eq(EventFieldPO::getFieldId, fieldId));
    }

    // —— 映射 ——

    private EventFieldPO toPO(EventField ef) {
        EventFieldPO po = new EventFieldPO();
        po.setId(ef.getId());
        po.setEventTypeCode(ef.getEventTypeCode());
        po.setFieldId(ef.getFieldId());
        po.setPurposesJson(serializePurposes(ef.getPurposes()));
        po.setDerived(ef.isDerived() ? 1 : 0);
        return po;
    }

    private EventField toDomain(EventFieldPO po) {
        return EventField.rehydrate(po.getId(), po.getEventTypeCode(), po.getFieldId(),
                deserializePurposes(po.getPurposesJson()),
                po.getDerived() != null && po.getDerived() == 1);
    }

    private String serializePurposes(Set<EventPurpose> purposes) {
        if (purposes == null || purposes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(purposes.stream().map(Enum::name).toList());
        } catch (Exception ex) {
            throw new IllegalStateException("序列化事件字段用途失败", ex);
        }
    }

    private Set<EventPurpose> deserializePurposes(String json) {
        if (json == null || json.isBlank()) {
            return EnumSet.noneOf(EventPurpose.class);
        }
        try {
            List<String> names = objectMapper.readValue(json, STRING_LIST);
            Set<EventPurpose> result = new LinkedHashSet<>();
            for (String n : names) {
                if (n != null && !n.isBlank()) {
                    result.add(EventPurpose.valueOf(n.trim()));
                }
            }
            return result;
        } catch (Exception ex) {
            // 容错：历史脏数据不阻断读取。
            return EnumSet.noneOf(EventPurpose.class);
        }
    }
}
