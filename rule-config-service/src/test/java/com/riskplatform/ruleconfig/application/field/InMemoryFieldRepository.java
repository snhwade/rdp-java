package com.riskplatform.ruleconfig.application.field;

import com.riskplatform.ruleconfig.domain.field.DerivedField;
import com.riskplatform.ruleconfig.domain.field.FieldDefinition;
import com.riskplatform.ruleconfig.domain.field.FieldRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版字段仓储，用于字段库应用服务的单元与属性测试。
 *
 * <p>关键点：{@link #existsFieldByCode} / {@link #findFieldByCode} 以<strong>精确等值</strong>
 * 实现，模拟数据库唯一键 + 精确等值查询语义（R3.4），不做任何前缀/模糊匹配。
 */
public class InMemoryFieldRepository implements FieldRepository {

    private final Map<Long, FieldDefinition> fields = new LinkedHashMap<>();
    private final Map<Long, DerivedField> derived = new LinkedHashMap<>();
    private final AtomicLong fieldSeq = new AtomicLong();
    private final AtomicLong derivedSeq = new AtomicLong();

    @Override
    public FieldDefinition saveField(FieldDefinition field) {
        // 模拟数据库唯一键：插入前若 code 精确重复则拒绝。
        if (existsFieldByCode(field.code())) {
            throw new IllegalStateException("duplicate code (unique key): " + field.code());
        }
        long id = fieldSeq.incrementAndGet();
        FieldDefinition saved = new FieldDefinition(id, field.code(), field.name(), field.dataType(),
                field.label(), field.enabled());
        fields.put(id, saved);
        return saved;
    }

    @Override
    public FieldDefinition updateField(FieldDefinition field) {
        fields.put(field.id(), field);
        return field;
    }

    @Override
    public boolean deleteField(Long id) {
        return fields.remove(id) != null;
    }

    @Override
    public Optional<FieldDefinition> findFieldById(Long id) {
        return Optional.ofNullable(fields.get(id));
    }

    @Override
    public Optional<FieldDefinition> findFieldByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return fields.values().stream().filter(f -> code.equals(f.code())).findFirst();
    }

    @Override
    public boolean existsFieldByCode(String code) {
        if (code == null) {
            return false;
        }
        return fields.values().stream().anyMatch(f -> code.equals(f.code()));
    }

    @Override
    public boolean existsFieldByName(String name) {
        if (name == null) {
            return false;
        }
        return fields.values().stream().anyMatch(f -> name.equals(f.name()));
    }

    @Override
    public Optional<FieldDefinition> findFieldByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return fields.values().stream().filter(f -> name.equals(f.name())).findFirst();
    }

    @Override
    public List<FieldDefinition> listFields() {
        return new ArrayList<>(fields.values());
    }

    @Override
    public DerivedField saveDerived(DerivedField d) {
        long id = derivedSeq.incrementAndGet();
        DerivedField saved = new DerivedField(id, d.eventTypeCode(), d.name(), d.expression(), d.enabled());
        derived.put(id, saved);
        return saved;
    }

    @Override
    public DerivedField updateDerived(DerivedField d) {
        derived.put(d.id(), d);
        return d;
    }

    @Override
    public boolean deleteDerived(Long id) {
        return derived.remove(id) != null;
    }

    @Override
    public Optional<DerivedField> findDerivedById(Long id) {
        return Optional.ofNullable(derived.get(id));
    }

    @Override
    public List<DerivedField> listDerived(String eventTypeCode) {
        return derived.values().stream()
                .filter(d -> eventTypeCode == null || eventTypeCode.equals(d.eventTypeCode()))
                .toList();
    }

    @Override
    public List<DerivedField> findEnabledDerived(String eventTypeCode) {
        return derived.values().stream()
                .filter(d -> eventTypeCode != null && eventTypeCode.equals(d.eventTypeCode()) && d.enabled())
                .toList();
    }

    @Override
    public List<DerivedField> findDerivedReferencing(String token) {
        if (token == null || token.isBlank()) {
            return List.of();
        }
        return derived.values().stream()
                .filter(d -> d.expression() != null && d.expression().contains(token))
                .toList();
    }
}
