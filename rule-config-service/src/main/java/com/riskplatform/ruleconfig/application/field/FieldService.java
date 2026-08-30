package com.riskplatform.ruleconfig.application.field;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.error.RuleConfigErrorCode;
import com.riskplatform.ruleconfig.domain.field.DerivedField;
import com.riskplatform.ruleconfig.domain.field.FieldDefinition;
import com.riskplatform.ruleconfig.domain.field.FieldRelations;
import com.riskplatform.ruleconfig.domain.field.FieldReferenceChecker;
import com.riskplatform.ruleconfig.domain.field.FieldRepository;
import com.riskplatform.ruleconfig.domain.field.FieldImportResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字段库与衍生字段应用服务（S7）。
 *
 * <p>管理字段库与衍生字段，并提供衍生字段计算：取该事件类型所有启用衍生字段，按 Aviator 表达式
 * 对原始上下文求值，将结果合并进上下文返回，供后续规则/决策表/评分卡引用。
 *
 * <p>删除与改 code 前经 {@link FieldReferenceChecker} 做血缘阻断（参数管理 Q1-B）。
 */
public class FieldService {

    private final FieldRepository repository;
    private final FieldReferenceChecker referenceChecker;
    private final AviatorEvaluatorInstance aviator = AviatorEvaluator.newInstance();

    public FieldService(FieldRepository repository) {
        this(repository, FieldReferenceChecker.noop());
    }

    public FieldService(FieldRepository repository, FieldReferenceChecker referenceChecker) {
        this.repository = repository;
        this.referenceChecker = referenceChecker == null ? FieldReferenceChecker.noop() : referenceChecker;
    }

    // —— 字段库 ——

    /**
     * 创建全局字段（R3.2/R3.3/R3.4/R3.5）。
     *
     * <p>领域层校验必填项与受支持数据类型；应用层以精确等值预检 code 唯一性，
     * 仅在真实重复时拒绝（R3.4）。
     */
    public FieldDefinition createField(String code, String name, String dataType, String label) {
        FieldDefinition field = FieldDefinition.create(code, name, dataType, label); // R3.3/R3.5
        if (repository.existsFieldByCode(code)) { // R3.4 精确等值去重
            throw BizException.duplicate("字段 code 已存在: " + code);
        }
        if (repository.existsFieldByName(name)) {
            throw BizException.duplicate("字段 name 已存在: " + name);
        }
        return repository.saveField(field);
    }

    /**
     * 编辑全局字段（R3.5）。改动 code 时仍以精确等值校验唯一性（R3.4）；
     * 若 code 变化且仍被引用则拒绝（FIELD.IN_USE）。
     */
    public FieldDefinition updateField(Long id, String code, String name, String dataType, String label,
                                       boolean enabled) {
        FieldDefinition existing = repository.findFieldById(id)
                .orElseThrow(() -> BizException.notFound("字段不存在: id=" + id));
        FieldDefinition updated = FieldDefinition.of(id, code, name, dataType, label, enabled); // R3.3/R3.5
        // 仅当 code 真正变化时检查唯一性，避免把自身判为重复（R3.4 不误判）。
        if (!existing.code().equals(code)) {
            assertNotReferenced(existing.id(), existing.code(), "修改字段编码");
            repository.findFieldByCode(code).ifPresent(other -> {
                throw BizException.duplicate("字段 code 已存在: " + code);
            });
        }
        if (!existing.name().equals(name)) {
            repository.findFieldByName(name).ifPresent(other -> {
                if (!other.id().equals(id)) {
                    throw BizException.duplicate("字段 name 已存在: " + name);
                }
            });
        }
        return repository.updateField(updated);
    }

    public void deleteField(Long id) {
        FieldDefinition existing = repository.findFieldById(id)
                .orElseThrow(() -> BizException.notFound("字段不存在: id=" + id));
        assertNotReferenced(existing.id(), existing.code(), "删除字段");
        repository.deleteField(id);
    }

    public List<FieldDefinition> listFields() {
        return repository.listFields();
    }

    /**
     * 批量导入字段（R3.6）：逐条校验，持久化全部校验通过的记录，
     * 为每条校验未通过的记录返回失败原因。单条失败不影响其它条目。
     */
    public FieldImportResult importFields(List<FieldImportRecord> records) {
        List<FieldDefinition> imported = new ArrayList<>();
        List<FieldImportResult.Failure> failures = new ArrayList<>();
        // 同一批次内已出现的 code，用于检测批内重复（避免唯一键异常）。
        Set<String> seenCodes = new LinkedHashSet<>();
        int index = 0;
        for (FieldImportRecord record : records == null ? List.<FieldImportRecord>of() : records) {
            String code = record == null ? null : record.code();
            try {
                if (record == null) {
                    throw BizException.missingField("record");
                }
                if (code != null && !seenCodes.add(code)) {
                    throw BizException.duplicate("导入批次内 code 重复: " + code);
                }
                imported.add(createField(record.code(), record.name(), record.dataType(), record.label()));
            } catch (BizException ex) {
                failures.add(new FieldImportResult.Failure(index, code, reasonOf(ex)));
            }
            index++;
        }
        return new FieldImportResult(imported, failures);
    }

    /**
     * 字段关联关系查询（R3.7）：返回引用该字段的事件、枚举值与衍生字段。
     *
     * <p>当前依据既有 {@code derived_field} 表：表达式引用该字段（code 或 name）的衍生字段，
     * 以及这些衍生字段所属的事件。枚举值关联待事件字段/枚举绑定落地后补充，先返回空集合。
     */
    public FieldRelations relations(Long fieldId) {
        FieldDefinition field = repository.findFieldById(fieldId)
                .orElseThrow(() -> BizException.notFound("字段不存在: id=" + fieldId));

        Map<Long, DerivedField> derivedById = new LinkedHashMap<>();
        for (String token : new LinkedHashSet<>(List.of(field.code(), field.name()))) {
            for (DerivedField d : repository.findDerivedReferencing(token)) {
                if (d.id() != null) {
                    derivedById.put(d.id(), d);
                }
            }
        }
        List<DerivedField> derived = new ArrayList<>(derivedById.values());
        // 引用该字段的事件 = 这些衍生字段所属事件去重。
        List<String> events = derived.stream()
                .map(DerivedField::eventTypeCode)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();
        List<FieldRelations.EnumValueRef> enumValues = List.of();
        List<String> blocking = referenceChecker.findReferences(field.id(), field.code());
        return new FieldRelations(field.id(), field.code(), field.name(), events, enumValues, derived, blocking);
    }

    private void assertNotReferenced(Long fieldId, String fieldCode, String action) {
        List<String> refs = referenceChecker.findReferences(fieldId, fieldCode);
        if (refs != null && !refs.isEmpty()) {
            throw new BizException(RuleConfigErrorCode.FIELD_IN_USE,
                    action + "失败，字段仍被引用：" + String.join("、", refs)
                            + "。请先解除引用后再操作。");
        }
    }

    private static String reasonOf(BizException ex) {
        if (ex.getFields() != null && !ex.getFields().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            ex.getFields().forEach((k, v) -> {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(k).append(": ").append(v);
            });
            return sb.toString();
        }
        return ex.getMessage();
    }

    /** 批量导入单条记录。 */
    public record FieldImportRecord(String code, String name, String dataType, String label) {
    }

    // —— 衍生字段 ——

    public DerivedField createDerived(String eventTypeCode, String name, String expression) {
        // 创建前用 Aviator 校验表达式可编译
        try {
            aviator.compile(expression, true);
        } catch (Exception e) {
            throw new BizException(com.riskplatform.common.error.CommonErrorCode.INVALID_FIELD,
                    "衍生字段表达式语法错误: " + e.getMessage());
        }
        return repository.saveDerived(DerivedField.create(eventTypeCode, name, expression));
    }

    public DerivedField updateDerived(Long id, String name, String expression, boolean enabled) {
        DerivedField existing = repository.findDerivedById(id)
                .orElseThrow(() -> BizException.notFound("衍生字段不存在: id=" + id));
        return repository.updateDerived(new DerivedField(id, existing.eventTypeCode(), name, expression, enabled));
    }

    public void deleteDerived(Long id) {
        repository.deleteDerived(id);
    }

    public List<DerivedField> listDerived(String eventTypeCode) {
        return repository.listDerived(eventTypeCode);
    }

    /**
     * 计算衍生字段：对该事件类型所有启用衍生字段求值，结果合并进上下文返回。
     * 单个表达式异常则跳过该衍生字段（不影响其它），保证健壮。
     */
    public Map<String, Object> computeDerived(String eventTypeCode, Map<String, Object> context) {
        Map<String, Object> result = new LinkedHashMap<>(context == null ? Map.of() : context);
        for (DerivedField d : repository.findEnabledDerived(eventTypeCode)) {
            try {
                Object v = aviator.execute(d.expression(), result, true);
                result.put(d.name(), v);
            } catch (Exception ignored) {
                // 衍生字段计算失败跳过，不阻断其它字段
            }
        }
        return result;
    }
}
