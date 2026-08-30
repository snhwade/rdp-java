package com.riskplatform.ruleconfig.domain.dict;

import com.riskplatform.common.error.ValidationException;

import java.util.regex.Pattern;

/**
 * 风险等级字典聚合根（R12.1）。
 *
 * <p>较风险类型多 order_no（排序号）。code 全局唯一（由应用层校验）。
 */
public class RiskLevel {

    public static final int CODE_MAX = 64;
    public static final int NAME_MAX = 128;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private String code;
    private String name;
    private int orderNo;
    private DictStatus status;

    private RiskLevel() {
    }

    /** 工厂方法：创建启用状态的风险等级并校验。 */
    public static RiskLevel create(String code, String name, int orderNo) {
        RiskLevel t = new RiskLevel();
        t.code = code;
        t.name = name;
        t.orderNo = orderNo;
        t.status = DictStatus.ENABLED;
        t.validate();
        return t;
    }

    /** 从持久化重建（不重复校验）。 */
    public static RiskLevel rehydrate(Long id, String code, String name, int orderNo, DictStatus status) {
        RiskLevel t = new RiskLevel();
        t.id = id;
        t.code = code;
        t.name = name;
        t.orderNo = orderNo;
        t.status = status;
        return t;
    }

    /** 更新名称、排序号与状态（code 不可变）。 */
    public void update(String name, int orderNo, DictStatus status) {
        this.name = name;
        this.orderNo = orderNo;
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

    public int getOrderNo() {
        return orderNo;
    }

    public DictStatus getStatus() {
        return status;
    }
}
