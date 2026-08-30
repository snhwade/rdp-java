package com.riskplatform.ruleconfig.domain.scenario;

import com.riskplatform.common.error.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 场景聚合根（R10）。
 *
 * <p>场景作为业务条线维度，关联事件与决策资产，作为管理与权限边界。
 *
 * <p>不变式：
 * <ul>
 *   <li>name 长度 1..128（R10.1）</li>
 *   <li>code 长度 1..64 且仅由字母、数字、下划线组成（R10.1）</li>
 *   <li>创建时默认状态 ENABLED（R10.1）</li>
 *   <li>关联事件编码去重（R10.1：一个场景可关联多个事件）</li>
 * </ul>
 *
 * <p>本类为纯领域对象，不依赖框架。校验失败抛出 {@link ValidationException}（聚合字段级错误）。
 */
public class Scenario {

    public static final int NAME_MAX = 128;
    public static final int CODE_MAX = 64;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private String code;
    private String name;
    private ScenarioStatus status;
    /** 关联事件类型编码列表（去重、保持插入顺序）。 */
    private final List<String> eventTypeCodes = new ArrayList<>();

    private Scenario() {
    }

    /** 工厂方法：创建一个启用状态的场景，并执行输入校验。 */
    public static Scenario create(String code, String name, List<String> eventTypeCodes) {
        Scenario s = new Scenario();
        s.code = code;
        s.name = name;
        s.status = ScenarioStatus.ENABLED;
        s.replaceEvents(eventTypeCodes);
        s.validate();
        return s;
    }

    /** 从持久化重建（不重复校验）。 */
    public static Scenario rehydrate(Long id, String code, String name, ScenarioStatus status,
                                     List<String> eventTypeCodes) {
        Scenario s = new Scenario();
        s.id = id;
        s.code = code;
        s.name = name;
        s.status = status;
        if (eventTypeCodes != null) {
            s.eventTypeCodes.addAll(new LinkedHashSet<>(eventTypeCodes));
        }
        return s;
    }

    /** 校验 name 与 code，违反不变式时抛出聚合字段错误。 */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (name == null || name.isEmpty()) {
            errors.field("name", "必填");
        } else if (name.length() > NAME_MAX) {
            errors.field("name", "长度不能超过 " + NAME_MAX + " 个字符");
        }
        if (code == null || code.isEmpty()) {
            errors.field("code", "必填");
        } else if (code.length() > CODE_MAX) {
            errors.field("code", "长度不能超过 " + CODE_MAX + " 个字符");
        } else if (!CODE_PATTERN.matcher(code).matches()) {
            errors.field("code", "只能包含字母、数字与下划线");
        }
        errors.throwIfAny();
    }

    /** 更新名称（R10）。 */
    public void rename(String name) {
        this.name = name;
        validate();
    }

    /** 全量替换关联事件（去重，保持顺序）。 */
    public void replaceEvents(List<String> codes) {
        eventTypeCodes.clear();
        if (codes != null) {
            for (String c : codes) {
                if (c != null && !c.isBlank() && !eventTypeCodes.contains(c)) {
                    eventTypeCodes.add(c);
                }
            }
        }
    }

    /** 禁用（R10）。 */
    public void disable() {
        this.status = ScenarioStatus.DISABLED;
    }

    /** 启用。 */
    public void enable() {
        this.status = ScenarioStatus.ENABLED;
    }

    public boolean isEnabled() {
        return this.status == ScenarioStatus.ENABLED;
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

    public ScenarioStatus getStatus() {
        return status;
    }

    /** 返回不可变的关联事件编码列表。 */
    public List<String> getEventTypeCodes() {
        return Collections.unmodifiableList(eventTypeCodes);
    }
}
