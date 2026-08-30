package com.riskplatform.ruleconfig.infrastructure.eventtype;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.eventtype.EventKind;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;
import com.riskplatform.ruleconfig.domain.eventtype.EventType;
import com.riskplatform.ruleconfig.domain.eventtype.EventTypeRepository;
import com.riskplatform.ruleconfig.domain.eventtype.EventTypeStatus;
import org.springframework.stereotype.Repository;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * EventType 仓储 MyBatis-Plus 实现（R1 / risk-console-redesign R2）。
 *
 * <p>purposes 以 JSON 数组字符串持久化到 purposes_json 列，使用共享的 {@link ObjectMapper}
 * 手动序列化/反序列化为 {@link EventPurpose} 集合。
 */
@Repository
public class EventTypeRepositoryImpl implements EventTypeRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final EventTypeMapper mapper;
    private final ObjectMapper objectMapper;

    public EventTypeRepositoryImpl(EventTypeMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public EventType save(EventType eventType) {
        EventTypePO po = toPO(eventType);
        mapper.insert(po);
        eventType.assignId(po.getId());
        return eventType;
    }

    @Override
    public void update(EventType eventType) {
        mapper.updateById(toPO(eventType));
    }

    @Override
    public Optional<EventType> findByCode(String code) {
        EventTypePO po = mapper.selectOne(new LambdaQueryWrapper<EventTypePO>()
                .eq(EventTypePO::getCode, code));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<EventType> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return mapper.exists(new LambdaQueryWrapper<EventTypePO>().eq(EventTypePO::getCode, code));
    }

    @Override
    public boolean existsByCodeExcludingId(String code, Long excludeId) {
        LambdaQueryWrapper<EventTypePO> wrapper = new LambdaQueryWrapper<EventTypePO>()
                .eq(EventTypePO::getCode, code);
        if (excludeId != null) {
            wrapper.ne(EventTypePO::getId, excludeId);
        }
        return mapper.exists(wrapper);
    }

    @Override
    public List<EventType> findAll() {
        return mapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public List<EventType> findByScenarioId(Long scenarioId) {
        return mapper.selectList(new LambdaQueryWrapper<EventTypePO>()
                        .eq(EventTypePO::getScenarioId, scenarioId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    private EventTypePO toPO(EventType e) {
        EventTypePO po = new EventTypePO();
        po.setId(e.getId());
        po.setCode(e.getCode());
        po.setName(e.getName());
        po.setStatus(e.getStatus() == EventTypeStatus.ENABLED ? 1 : 0);
        po.setScenarioId(e.getScenarioId());
        po.setEventKind(e.getEventKind() == null ? null : e.getEventKind().name());
        po.setPurposesJson(serializePurposes(e.getPurposes()));
        return po;
    }

    private EventType toDomain(EventTypePO po) {
        EventTypeStatus status = po.getStatus() != null && po.getStatus() == 1
                ? EventTypeStatus.ENABLED : EventTypeStatus.DISABLED;
        EventKind kind = po.getEventKind() == null || po.getEventKind().isBlank()
                ? null : EventKind.valueOf(po.getEventKind());
        return EventType.rehydrate(po.getId(), po.getCode(), po.getName(), status,
                po.getScenarioId(), deserializePurposes(po.getPurposesJson()), kind);
    }

    private String serializePurposes(Set<EventPurpose> purposes) {
        if (purposes == null || purposes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(purposes.stream().map(Enum::name).toList());
        } catch (Exception ex) {
            throw new IllegalStateException("序列化事件用途失败", ex);
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
            // 容错：历史脏数据不阻断读取
            return EnumSet.noneOf(EventPurpose.class);
        }
    }
}
