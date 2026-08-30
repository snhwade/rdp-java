package com.riskplatform.ruleconfig.infrastructure.field;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.field.DerivedField;
import com.riskplatform.ruleconfig.domain.field.FieldDefinition;
import com.riskplatform.ruleconfig.domain.field.FieldRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 字段库与衍生字段仓储 MyBatis-Plus 实现（S7）。 */
@Repository
public class FieldRepositoryImpl implements FieldRepository {

    private final FieldMappers.FieldDefinitionMapper fieldMapper;
    private final FieldMappers.DerivedFieldMapper derivedMapper;

    public FieldRepositoryImpl(FieldMappers.FieldDefinitionMapper fieldMapper,
                               FieldMappers.DerivedFieldMapper derivedMapper) {
        this.fieldMapper = fieldMapper;
        this.derivedMapper = derivedMapper;
    }

    // —— 字段库 ——

    @Override
    public FieldDefinition saveField(FieldDefinition field) {
        FieldDefinitionPO po = toFieldPO(field);
        fieldMapper.insert(po);
        return toFieldDomain(po);
    }

    @Override
    public FieldDefinition updateField(FieldDefinition field) {
        fieldMapper.updateById(toFieldPO(field));
        return field;
    }

    @Override
    public boolean deleteField(Long id) {
        return fieldMapper.deleteById(id) > 0;
    }

    @Override
    public Optional<FieldDefinition> findFieldById(Long id) {
        return Optional.ofNullable(fieldMapper.selectById(id)).map(this::toFieldDomain);
    }

    @Override
    public Optional<FieldDefinition> findFieldByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        // 精确等值查询（R3.4）：使用 eq，禁止 LIKE/前缀/模糊匹配。
        FieldDefinitionPO po = fieldMapper.selectOne(new LambdaQueryWrapper<FieldDefinitionPO>()
                .eq(FieldDefinitionPO::getCode, code));
        return Optional.ofNullable(po).map(this::toFieldDomain);
    }

    @Override
    public Optional<FieldDefinition> findFieldByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        FieldDefinitionPO po = fieldMapper.selectOne(new LambdaQueryWrapper<FieldDefinitionPO>()
                .eq(FieldDefinitionPO::getName, name));
        return Optional.ofNullable(po).map(this::toFieldDomain);
    }

    @Override
    public boolean existsFieldByCode(String code) {
        if (code == null) {
            return false;
        }
        // 精确等值存在性判定（R3.4）。
        return fieldMapper.exists(new LambdaQueryWrapper<FieldDefinitionPO>()
                .eq(FieldDefinitionPO::getCode, code));
    }

    @Override
    public boolean existsFieldByName(String name) {
        if (name == null) {
            return false;
        }
        return fieldMapper.exists(new LambdaQueryWrapper<FieldDefinitionPO>()
                .eq(FieldDefinitionPO::getName, name));
    }

    @Override
    public List<FieldDefinition> listFields() {
        return fieldMapper.selectList(new LambdaQueryWrapper<>()).stream().map(this::toFieldDomain).toList();
    }

    // —— 衍生字段 ——

    @Override
    public DerivedField saveDerived(DerivedField derived) {
        DerivedFieldPO po = toDerivedPO(derived);
        derivedMapper.insert(po);
        return toDerivedDomain(po);
    }

    @Override
    public DerivedField updateDerived(DerivedField derived) {
        derivedMapper.updateById(toDerivedPO(derived));
        return derived;
    }

    @Override
    public boolean deleteDerived(Long id) {
        return derivedMapper.deleteById(id) > 0;
    }

    @Override
    public Optional<DerivedField> findDerivedById(Long id) {
        return Optional.ofNullable(derivedMapper.selectById(id)).map(this::toDerivedDomain);
    }

    @Override
    public List<DerivedField> listDerived(String eventTypeCode) {
        LambdaQueryWrapper<DerivedFieldPO> w = new LambdaQueryWrapper<>();
        if (eventTypeCode != null && !eventTypeCode.isBlank()) {
            w.eq(DerivedFieldPO::getEventTypeCode, eventTypeCode);
        }
        return derivedMapper.selectList(w).stream().map(this::toDerivedDomain).toList();
    }

    @Override
    public List<DerivedField> findEnabledDerived(String eventTypeCode) {
        return derivedMapper.selectList(new LambdaQueryWrapper<DerivedFieldPO>()
                        .eq(DerivedFieldPO::getEventTypeCode, eventTypeCode)
                        .eq(DerivedFieldPO::getEnabled, 1))
                .stream().map(this::toDerivedDomain).toList();
    }

    @Override
    public List<DerivedField> findDerivedReferencing(String token) {
        if (token == null || token.isBlank()) {
            return List.of();
        }
        // 关联发现：表达式包含该字段标识（子串匹配）。仅用于关联关系展示，
        // 不参与 code 去重（去重为精确等值）。
        return derivedMapper.selectList(new LambdaQueryWrapper<DerivedFieldPO>()
                        .like(DerivedFieldPO::getExpression, token))
                .stream().map(this::toDerivedDomain).toList();
    }

    // —— 映射 ——

    private FieldDefinitionPO toFieldPO(FieldDefinition f) {
        FieldDefinitionPO po = new FieldDefinitionPO();
        po.setId(f.id());
        po.setCode(f.code());
        po.setName(f.name());
        po.setDataType(f.dataType());
        po.setLabel(f.label());
        po.setEnabled(f.enabled() ? 1 : 0);
        return po;
    }

    private FieldDefinition toFieldDomain(FieldDefinitionPO po) {
        return new FieldDefinition(po.getId(), po.getCode(), po.getName(), po.getDataType(), po.getLabel(),
                po.getEnabled() == null || po.getEnabled() == 1);
    }

    private DerivedFieldPO toDerivedPO(DerivedField d) {
        DerivedFieldPO po = new DerivedFieldPO();
        po.setId(d.id());
        po.setEventTypeCode(d.eventTypeCode());
        po.setName(d.name());
        po.setExpression(d.expression());
        po.setEnabled(d.enabled() ? 1 : 0);
        return po;
    }

    private DerivedField toDerivedDomain(DerivedFieldPO po) {
        return new DerivedField(po.getId(), po.getEventTypeCode(), po.getName(), po.getExpression(),
                po.getEnabled() == null || po.getEnabled() == 1);
    }
}
