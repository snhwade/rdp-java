package com.riskplatform.ruleconfig.domain.field;

import com.riskplatform.common.error.ValidationException;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 字段库条目（S7 / R3）：统一管理全局字段的英文标识/命名/类型/含义。
 *
 * <p>不变式（R3）：
 * <ul>
 *   <li>code（英文标识）必填，仅由字母、数字、下划线组成，长度 1..64，且全局唯一（唯一性由应用层 + 数据库唯一键保证，R3.4）</li>
 *   <li>name（字段名称）必填，长度 1..64（R3.5）</li>
 *   <li>dataType 必填，且取受支持的数据类型集合之一（至少 String/Double/Integer/Boolean/Date，R3.3）</li>
 * </ul>
 *
 * @param id       主键
 * @param code     字段英文标识（全局唯一）
 * @param name     字段名称
 * @param dataType 数据类型
 * @param label    含义说明
 * @param enabled  是否启用
 */
public record FieldDefinition(Long id, String code, String name, String dataType, String label, boolean enabled) {

    public static final int CODE_MAX = 64;
    public static final int NAME_MAX = 64;

    private static final java.util.regex.Pattern CODE_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9_]+$");

    /**
     * 受支持的字段数据类型集合（R3.3）。
     *
     * <p>至少包含需求要求的 String/Double/Integer/Boolean/Date；并保留既有数据使用的 LONG，
     * 以兼容历史字段。校验时大小写不敏感。
     */
    public static final Set<String> SUPPORTED_DATA_TYPES = Set.of(
            "STRING", "DOUBLE", "INTEGER", "BOOLEAN", "DATE", "LONG");

    /** 创建一个启用状态的字段库条目，并执行输入校验（R3.2/R3.3/R3.5）。 */
    public static FieldDefinition create(String code, String name, String dataType, String label) {
        validateInput(code, name, dataType);
        return new FieldDefinition(null, code, name, dataType, label, true);
    }

    /** 重建一个已有字段（含状态），并执行输入校验。供编辑使用。 */
    public static FieldDefinition of(Long id, String code, String name, String dataType, String label, boolean enabled) {
        validateInput(code, name, dataType);
        return new FieldDefinition(id, code, name, dataType, label, enabled);
    }

    /**
     * 校验字段输入：缺失必填项时以字段名累积错误并抛出（R3.5 返回缺失字段名），
     * dataType 不在受支持集合时报错（R3.3）。
     */
    public static void validateInput(String code, String name, String dataType) {
        ValidationException.Builder errors = ValidationException.builder();
        if (code == null || code.isBlank()) {
            errors.field("code", "必填");
        } else if (code.length() > CODE_MAX) {
            errors.field("code", "长度不能超过 " + CODE_MAX + " 个字符");
        } else if (!CODE_PATTERN.matcher(code).matches()) {
            errors.field("code", "只能包含字母、数字与下划线");
        }
        if (name == null || name.isBlank()) {
            errors.field("name", "必填");
        } else if (name.length() > NAME_MAX) {
            errors.field("name", "长度不能超过 " + NAME_MAX + " 个字符");
        }
        if (dataType == null || dataType.isBlank()) {
            errors.field("dataType", "必填");
        } else if (!isSupportedDataType(dataType)) {
            errors.field("dataType", "不受支持的数据类型，应取其一: " + sortedSupportedTypes());
        }
        errors.throwIfAny();
    }

    /** 数据类型是否受支持（大小写不敏感，R3.3）。 */
    public static boolean isSupportedDataType(String dataType) {
        return dataType != null && SUPPORTED_DATA_TYPES.contains(dataType.toUpperCase(Locale.ROOT));
    }

    private static String sortedSupportedTypes() {
        return String.join("/", new LinkedHashSet<>(SUPPORTED_DATA_TYPES));
    }
}
