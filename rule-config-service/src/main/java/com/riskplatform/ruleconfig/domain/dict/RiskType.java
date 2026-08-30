package com.riskplatform.ruleconfig.domain.dict;

import com.riskplatform.common.error.ValidationException;

import java.util.regex.Pattern;

/**
 * 风险类型字典聚合根（R12.1）。
 *
 * <p>不变式：
 * <ul>
 *   <li>code 长度 1..64，仅由字母、数字、下划线组成，全局唯一（唯一性由应用层校验）。</li>
 *   <li>name 长度 1..128。</li>
 *   <li>创建时默认状态 ENABLED。</li>
 * </ul>
 */
public class RiskType {

    public static final int CODE_MAX = 64;
    public static final int NAME_MAX = 128;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private String code;
    private String name;
    private DictStatus status;

    private RiskType() {
    }

    /** 工厂方法：创建启用状态的风险类型并校验。 */
    public static RiskType create(String code, String name) {
        RiskType t = new RiskType();
        t.code = code;
        t.name = name;
        t.status = DictStatus.ENABLED;
        t.validate();
        return t;
    }

    /** 从持久化重建（不重复校验）。 */
    public static RiskType rehydrate(Long id, String code, String name, DictStatus status) {
        RiskType t = new RiskType();
        t.id = id;
        t.code = code;
        t.name = name;
        t.status = status;
        return t;
    }

    /** 更新名称与状态（code 不可变）。 */
    public void update(String name, DictStatus status) {
        this.name = name;
        if (status != null) {
            this.status = status;
        }
        validate();
    }

    /** 校验不变式，违反时抛出聚合字段错误。 */
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

    public DictStatus getStatus() {
        return status;
    }
}
