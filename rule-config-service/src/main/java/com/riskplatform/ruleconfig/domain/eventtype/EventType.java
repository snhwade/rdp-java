package com.riskplatform.ruleconfig.domain.eventtype;

import com.riskplatform.common.error.ValidationException;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 事件类型聚合根（R1 / risk-console-redesign R2）。
 *
 * <p>不变式：
 * <ul>
 *   <li>name 长度 1..100（R1.1/R1.3）</li>
 *   <li>code 长度 1..64 且仅由字母、数字、下划线组成（R1.1/R1.3）</li>
 *   <li>创建时默认状态 ENABLED（R1.1）</li>
 *   <li>事件用途 purposes 为 {COMPUTE, DECISION} 的非空子集（R2.3，Property 4）</li>
 *   <li>事件类型分型 eventKind 为 {DIMENSION, FACT} 二选一（R2.4）</li>
 * </ul>
 *
 * <p>本类为纯领域对象，不依赖框架。输入校验在 {@link #create} / {@link #validate} 中完成，
 * 校验失败抛出 {@link ValidationException}（聚合字段级错误）。
 *
 * <p>兼容性：保留既有 {@code create(code, name)} 与 {@code rehydrate(id, code, name, status)}
 * 两个工厂签名（既有调用方与测试依赖），新增带 scenarioId/purposes/eventKind 的重载。
 */
public class EventType {

    public static final int NAME_MAX = 100;
    public static final int CODE_MAX = 64;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private String code;
    private String name;
    private EventTypeStatus status;
    /** 所属业务场景ID（risk-console-redesign R2.2，必填）。 */
    private Long scenarioId;
    /** 事件类型分型 DIMENSION/FACT（R2.4，必填其一）。 */
    private EventKind eventKind;
    /** 事件用途多选（R2.3，非空子集）。保持插入顺序。 */
    private final Set<EventPurpose> purposes = new LinkedHashSet<>();

    private EventType() {
    }

    /**
     * 工厂方法（兼容旧签名）：创建一个启用状态的事件类型，仅校验 code/name。
     *
     * <p>新建带场景/用途/分型的事件请使用
     * {@link #create(String, String, Long, Set, EventKind)}。
     */
    public static EventType create(String code, String name) {
        EventType et = new EventType();
        et.code = code;
        et.name = name;
        et.status = EventTypeStatus.ENABLED;
        et.validate();
        return et;
    }

    /**
     * 工厂方法（risk-console-redesign R2.2）：创建一个启用状态的事件类型，
     * 校验 code/name 以及场景/用途/分型必填项与用途非空子集、分型二选一。
     */
    public static EventType create(String code, String name, Long scenarioId,
                                   Set<EventPurpose> purposes, EventKind eventKind) {
        EventType et = new EventType();
        et.code = code;
        et.name = name;
        et.status = EventTypeStatus.ENABLED;
        et.scenarioId = scenarioId;
        et.eventKind = eventKind;
        if (purposes != null) {
            et.purposes.addAll(purposes);
        }
        et.validate();
        return et;
    }

    /** 从持久化重建（不重复校验，兼容旧签名）。 */
    public static EventType rehydrate(Long id, String code, String name, EventTypeStatus status) {
        EventType et = new EventType();
        et.id = id;
        et.code = code;
        et.name = name;
        et.status = status;
        return et;
    }

    /** 从持久化重建（含扩展属性，不重复校验）。 */
    public static EventType rehydrate(Long id, String code, String name, EventTypeStatus status,
                                      Long scenarioId, Set<EventPurpose> purposes, EventKind eventKind) {
        EventType et = new EventType();
        et.id = id;
        et.code = code;
        et.name = name;
        et.status = status;
        et.scenarioId = scenarioId;
        et.eventKind = eventKind;
        if (purposes != null) {
            et.purposes.addAll(purposes);
        }
        return et;
    }

    /**
     * 校验不变式，违反时抛出聚合字段错误。
     *
     * <p>当 scenarioId/eventKind/purposes 均未设置（旧式仅 code/name 创建）时，仅校验 code/name，
     * 以兼容既有用法；一旦提供任一扩展属性即视为新建事件，对扩展必填项一并校验（R2.5）。
     */
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
        if (isExtended()) {
            validateExtended(errors);
        }
        errors.throwIfAny();
    }

    /** 是否提供了任一扩展属性（场景/用途/分型）。 */
    private boolean isExtended() {
        return scenarioId != null || eventKind != null || !purposes.isEmpty();
    }

    /** 扩展必填项校验：场景、用途非空子集、分型二选一（R2.3/R2.4/R2.5）。 */
    private void validateExtended(ValidationException.Builder errors) {
        if (scenarioId == null) {
            errors.field("scenarioId", "必填");
        }
        if (purposes.isEmpty()) {
            errors.field("purposes", "至少选择一个事件用途（计算/决策）");
        }
        if (eventKind == null) {
            errors.field("eventKind", "必填，取维度表(DIMENSION)或事实表(FACT)之一");
        }
    }

    /**
     * 编辑事件名称、所属业务场景、事件用途与事件类型分型（R2.7），并校验。
     */
    public void edit(String name, Long scenarioId, Set<EventPurpose> purposes, EventKind eventKind) {
        this.name = name;
        this.scenarioId = scenarioId;
        this.eventKind = eventKind;
        this.purposes.clear();
        if (purposes != null) {
            this.purposes.addAll(purposes);
        }
        // 编辑必校验全部扩展必填项（R2.5）
        ValidationException.Builder errors = ValidationException.builder();
        if (this.name == null || this.name.isEmpty()) {
            errors.field("name", "必填");
        } else if (this.name.length() > NAME_MAX) {
            errors.field("name", "长度不能超过 " + NAME_MAX + " 个字符");
        }
        validateExtended(errors);
        errors.throwIfAny();
    }

    /** 禁用（R1.4）。 */
    public void disable() {
        this.status = EventTypeStatus.DISABLED;
    }

    /** 启用。 */
    public void enable() {
        this.status = EventTypeStatus.ENABLED;
    }

    public boolean isEnabled() {
        return this.status == EventTypeStatus.ENABLED;
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

    public EventTypeStatus getStatus() {
        return status;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public EventKind getEventKind() {
        return eventKind;
    }

    /** 返回不可变的事件用途集合。 */
    public Set<EventPurpose> getPurposes() {
        return Collections.unmodifiableSet(purposes.isEmpty()
                ? EnumSet.noneOf(EventPurpose.class) : EnumSet.copyOf(purposes));
    }
}
