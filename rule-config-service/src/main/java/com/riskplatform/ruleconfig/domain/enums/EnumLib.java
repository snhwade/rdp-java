package com.riskplatform.ruleconfig.domain.enums;

import com.riskplatform.common.error.ValidationException;

import java.util.regex.Pattern;

/**
 * 枚举库聚合根（R12.2）。
 *
 * <p>不变式：code 长度 1..64 且仅字母数字下划线，全局唯一（应用层校验）；name 长度 1..128；
 * dataType 必填；创建默认 ENABLED。枚举值作为下属实体单独管理。
 */
public class EnumLib {

    public static final int CODE_MAX = 64;
    public static final int NAME_MAX = 128;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private String code;
    private String name;
    private EnumDataType dataType;
    private EnumStatus status;

    private EnumLib() {
    }

    /** 工厂方法：创建启用状态的枚举库并校验。 */
    public static EnumLib create(String code, String name, EnumDataType dataType) {
        EnumLib lib = new EnumLib();
        lib.code = code;
        lib.name = name;
        lib.dataType = dataType;
        lib.status = EnumStatus.ENABLED;
        lib.validate();
        return lib;
    }

    /** 从持久化重建（不重复校验）。 */
    public static EnumLib rehydrate(Long id, String code, String name, EnumDataType dataType, EnumStatus status) {
        EnumLib lib = new EnumLib();
        lib.id = id;
        lib.code = code;
        lib.name = name;
        lib.dataType = dataType;
        lib.status = status;
        return lib;
    }

    /** 更新名称、数据类型与状态（code 不可变）。 */
    public void update(String name, EnumDataType dataType, EnumStatus status) {
        this.name = name;
        if (dataType != null) {
            this.dataType = dataType;
        }
        if (status != null) {
            this.status = status;
        }
        validate();
    }

    /** 校验不变式。 */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (code == null || code.isEmpty()) {
            errors.field("code", "必填");
        } else if (code.length() > CODE_MAX) {
            errors.field("code", "长度不能超过 " + CODE_MAX + " 个字符");
        } else if (!CODE_PATTERN.matcher(code).matches()) {
            errors.field("code", "只能包含字母、数字与下划线");
        }
        if (name == null || name.isEmpty()) {
            errors.field("name", "必填");
        } else if (name.length() > NAME_MAX) {
            errors.field("name", "长度不能超过 " + NAME_MAX + " 个字符");
        }
        if (dataType == null) {
            errors.field("dataType", "必填");
        }
        errors.throwIfAny();
    }

    public Long getId() {
        return id;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public EnumDataType getDataType() {
        return dataType;
    }

    public EnumStatus getStatus() {
        return status;
    }

    /**
     * 枚举库状态（R12.2）。
     */
    public enum EnumStatus {
        /** 启用 */
        ENABLED,
        /** 禁用 */
        DISABLED
    }
}
