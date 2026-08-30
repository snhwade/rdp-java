package com.riskplatform.ruleconfig.domain.strategy;

import com.riskplatform.common.error.ValidationException;

import java.util.regex.Pattern;

/**
 * 策略定义聚合根（R3.1/R3.2/R3.3/R3.4；risk-console-redesign R5.2-R5.7）。
 *
 * <p>四类策略共表（strategy_def），按 {@link StrategyCategory} 区分语义；
 * 具体参数（验证方式/限额类型阈值/通知渠道/目标名单维度）以 paramsJson 透传，
 * 不在配置侧解释，决策聚合（5.3/5.4）按类别处理。
 *
 * <p>risk-console-redesign 为验证策略（{@link StrategyCategory#VERIFY}）新增两项属性：
 * <ul>
 *   <li>{@code priority}：验证策略优先级，整数 1..9999，数值越大优先级越高（R5.5/R5.6）。</li>
 *   <li>{@code scope}：验证策略固定为全场景通用（ANY_SCENARIO）。</li>
 * </ul>
 * 非验证类策略不要求上述属性，{@code priority}/{@code scope} 可为空，行为与历史一致。
 *
 * <p>不变式：
 * <ul>
 *   <li>category 必填（R3）</li>
 *   <li>code 长度 1..64 且仅由字母、数字、下划线组成</li>
 *   <li>name 长度 1..128</li>
 *   <li>验证策略：priority ∈ [1,9999] 且 scope 非空（R5.4/R5.5/R5.6）</li>
 *   <li>创建时默认状态 ENABLED</li>
 * </ul>
 *
 * <p>本类为纯领域对象，不依赖框架。校验失败抛出 {@link ValidationException}（聚合字段级错误）。
 */
public class StrategyDef {

    public static final int CODE_MAX = 64;
    public static final int NAME_MAX = 128;
    /** 验证策略优先级下界（含）（R5.5）。 */
    public static final int PRIORITY_MIN = 1;
    /** 验证策略优先级上界（含）（R5.5）。 */
    public static final int PRIORITY_MAX = 9999;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private StrategyCategory category;
    private String code;
    private String name;
    /** 策略参数 JSON（可空），由调用方/前端按类别约定结构。 */
    private String paramsJson;
    private StrategyStatus status;
    /** 验证策略优先级 1..9999，数值越大优先级越高（R5.5/R5.6）。非验证类可空。 */
    private Integer priority;
    /** 验证策略作用域（R5.4）。非验证类可空。 */
    private StrategyScope scope;

    private StrategyDef() {
    }

    /** 工厂方法：创建一个启用状态的策略定义（非验证语义，不带优先级/作用域），并执行输入校验。 */
    public static StrategyDef create(StrategyCategory category, String code, String name, String paramsJson) {
        StrategyDef s = new StrategyDef();
        s.category = category;
        s.code = code;
        s.name = name;
        s.paramsJson = paramsJson;
        s.status = StrategyStatus.ENABLED;
        s.validate();
        return s;
    }

    /**
     * 工厂方法：创建一个验证策略（{@link StrategyCategory#VERIFY}），带优先级与作用域，并执行输入校验（R5.3-R5.6）。
     *
     * @param code       策略代码
     * @param name       策略名称
     * @param priority   优先级 1..9999，数值越大优先级越高
     * @param scope      作用域（验证策略创建时固定为全场景通用）
     * @param paramsJson 策略参数 JSON（可空）
     */
    public static StrategyDef createVerify(String code, String name, Integer priority,
                                           StrategyScope scope, String paramsJson) {
        StrategyDef s = new StrategyDef();
        s.category = StrategyCategory.VERIFY;
        s.code = code;
        s.name = name;
        s.paramsJson = paramsJson;
        s.status = StrategyStatus.ENABLED;
        s.priority = priority;
        s.scope = scope;
        s.validate();
        return s;
    }

    /** 从持久化重建（不重复校验）。非验证类或历史数据用。 */
    public static StrategyDef rehydrate(Long id, StrategyCategory category, String code, String name,
                                        String paramsJson, StrategyStatus status) {
        return rehydrate(id, category, code, name, paramsJson, status, null, null);
    }

    /** 从持久化重建（不重复校验），含优先级与作用域。 */
    public static StrategyDef rehydrate(Long id, StrategyCategory category, String code, String name,
                                        String paramsJson, StrategyStatus status,
                                        Integer priority, StrategyScope scope) {
        StrategyDef s = new StrategyDef();
        s.id = id;
        s.category = category;
        s.code = code;
        s.name = name;
        s.paramsJson = paramsJson;
        s.status = status;
        s.priority = priority;
        s.scope = scope;
        return s;
    }

    /** 校验类别/编码/名称（验证策略另校验优先级与作用域），违反不变式时抛出聚合字段错误。 */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (category == null) {
            errors.field("category", "必填");
        }
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
        if (category == StrategyCategory.VERIFY) {
            validateVerifyFields(errors);
        }
        errors.throwIfAny();
    }

    /** 验证策略专属校验：优先级范围（R5.6）与作用域必填（R5.4）。 */
    private void validateVerifyFields(ValidationException.Builder errors) {
        if (priority == null) {
            errors.field("priority", "必填");
        } else if (priority < PRIORITY_MIN || priority > PRIORITY_MAX) {
            errors.field("priority", "取值范围为 " + PRIORITY_MIN + " 至 " + PRIORITY_MAX);
        }
        if (scope == null) {
            errors.field("scope", "必填（取具体业务场景或不限业务场景）");
        }
    }

    /** 更新名称与参数（R3）。 */
    public void update(String name, String paramsJson) {
        this.name = name;
        this.paramsJson = paramsJson;
        validate();
    }

    /** 更新验证策略的名称、优先级、作用域与参数（R5），并校验。 */
    public void updateVerify(String name, Integer priority, StrategyScope scope, String paramsJson) {
        this.name = name;
        this.priority = priority;
        this.scope = scope;
        this.paramsJson = paramsJson;
        validate();
    }

    /** 禁用。 */
    public void disable() {
        this.status = StrategyStatus.DISABLED;
    }

    /** 启用。 */
    public void enable() {
        this.status = StrategyStatus.ENABLED;
    }

    public boolean isEnabled() {
        return this.status == StrategyStatus.ENABLED;
    }

    public Long getId() {
        return id;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public StrategyCategory getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public StrategyStatus getStatus() {
        return status;
    }

    public Integer getPriority() {
        return priority;
    }

    public StrategyScope getScope() {
        return scope;
    }
}
