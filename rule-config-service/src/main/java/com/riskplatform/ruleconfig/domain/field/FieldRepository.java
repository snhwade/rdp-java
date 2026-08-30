package com.riskplatform.ruleconfig.domain.field;

import java.util.List;
import java.util.Optional;

/** 字段库与衍生字段仓储端口（S7）。 */
public interface FieldRepository {

    // —— 字段库 ——
    FieldDefinition saveField(FieldDefinition field);

    FieldDefinition updateField(FieldDefinition field);

    boolean deleteField(Long id);

    Optional<FieldDefinition> findFieldById(Long id);

    /**
     * 按字段 code 精确等值查询（R3.4）。
     *
     * <p>实现必须使用精确等值（{@code =}）匹配，禁止前缀/模糊匹配（如 LIKE），
     * 以确保 code 去重不误判（互为前缀的相似 code 不应判为重复）。
     */
    Optional<FieldDefinition> findFieldByCode(String code);

    Optional<FieldDefinition> findFieldByName(String name);

    /**
     * 字段 code 是否已真实存在（精确等值，R3.4）。
     */
    boolean existsFieldByCode(String code);

    /** 字段 name 是否已存在（精确等值，uk_field_name）。 */
    boolean existsFieldByName(String name);

    List<FieldDefinition> listFields();

    // —— 衍生字段 ——
    DerivedField saveDerived(DerivedField derived);

    DerivedField updateDerived(DerivedField derived);

    boolean deleteDerived(Long id);

    Optional<DerivedField> findDerivedById(Long id);

    List<DerivedField> listDerived(String eventTypeCode);

    /** 该事件类型下启用的衍生字段（计算用）。 */
    List<DerivedField> findEnabledDerived(String eventTypeCode);

    /**
     * 查找表达式中引用了给定字段标识（code 或 name）的衍生字段（R3.7 关联关系查询）。
     *
     * <p>用于"关联关系"展示，按子串包含匹配；此处为引用发现而非唯一性判定，
     * 与 code 去重（必须精确等值，{@link #existsFieldByCode}）语义不同。
     */
    List<DerivedField> findDerivedReferencing(String token);
}
