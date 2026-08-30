package com.riskplatform.ruleconfig.domain.dict;

import com.riskplatform.common.error.ValidationException;

import java.util.regex.Pattern;

/**
 * 决策标签字典聚合根（R12.1）。
 *
 * <p>较风险类型多 applicable_asset_type（适用资产类型，可空）。code 全局唯一（由应用层校验）。
 */
public class DecisionTag {

    public static final int CODE_MAX = 64;
    public static final int NAME_MAX = 128;
    public static final int ASSET_TYPE_MAX = 32;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private String code;
    private String name;
    private String applicableAssetType;
    private DictStatus status;

    private DecisionTag() {
    }

    /** 工厂方法：创建启用状态的决策标签并校验。 */
    public static DecisionTag create(String code, String name, String applicableAssetType) {
        DecisionTag t = new DecisionTag();
        t.code = code;
        t.name = name;
        t.applicableAssetType = applicableAssetType;
        t.status = DictStatus.ENABLED;
        t.validate();
        return t;
    }

    /** 从持久化重建（不重复校验）。 */
    public static DecisionTag rehydrate(Long id, String code, String name,
                                        String applicableAssetType, DictStatus status) {
        DecisionTag t = new DecisionTag();
        t.id = id;
        t.code = code;
        t.name = name;
        t.applicableAssetType = applicableAssetType;
        t.status = status;
        return t;
    }

    /** 更新名称、适用资产类型与状态（code 不可变）。 */
    public void update(String name, String applicableAssetType, DictStatus status) {
        this.name = name;
        this.applicableAssetType = applicableAssetType;
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
        if (applicableAssetType != null && applicableAssetType.length() > ASSET_TYPE_MAX) {
            errors.field("applicableAssetType", "长度不能超过 " + ASSET_TYPE_MAX + " 个字符");
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

    public String getApplicableAssetType() {
        return applicableAssetType;
    }

    public DictStatus getStatus() {
        return status;
    }
}
