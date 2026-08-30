package com.riskplatform.ruleconfig.domain.indicator;

import com.riskplatform.common.error.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 指标定义聚合根（R7）。
 *
 * <p>不变式：
 * <ul>
 *   <li>refName 长度 1..64 且仅 [A-Za-z0-9_]（R7.1/R7.3）</li>
 *   <li>dimensions / accScript 必填（R7.2）</li>
 *   <li>eventTypeCodes 保存时至少绑定一个事件（用于累计路由）</li>
 *   <li>windowDays 1..365；windowDays 须为切片粒度的整数倍（R7.5）</li>
 *   <li>仅 {@link #STATUS_ONLINE} 状态的指标参与累计</li>
 * </ul>
 */
public class IndicatorDefinition {

    public static final int REF_NAME_MAX = 64;
    public static final int NAME_MAX = 128;
    public static final int WINDOW_DAYS_MIN = 1;
    public static final int WINDOW_DAYS_MAX = 365;
    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_OFFLINE = "OFFLINE";
    private static final Pattern REF_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private Long groupId;
    private String refName;
    private String name;
    private String description;
    private List<String> eventTypeCodes;
    private List<String> dimensions;
    private int windowDays;
    private SliceGranularity sliceGranularity;
    private String accScript;
    private String defaultValueStrategy;
    private String status;
    private String templateType;
    private java.util.Map<String, Object> templateConfig;

    private IndicatorDefinition() {
    }

    public static IndicatorDefinition create(Long groupId, String refName, String name, String description,
                                             List<String> eventTypeCodes, List<String> dimensions,
                                             int windowDays, SliceGranularity sliceGranularity,
                                             String accScript, String defaultValueStrategy,
                                             String templateType, java.util.Map<String, Object> templateConfig) {
        IndicatorDefinition d = new IndicatorDefinition();
        d.groupId = groupId;
        d.refName = refName;
        d.name = name;
        d.description = description;
        d.eventTypeCodes = eventTypeCodes == null ? List.of() : new ArrayList<>(eventTypeCodes);
        d.dimensions = dimensions;
        d.windowDays = windowDays;
        d.sliceGranularity = sliceGranularity;
        d.accScript = accScript;
        d.defaultValueStrategy = defaultValueStrategy;
        d.templateType = templateType;
        d.templateConfig = templateConfig == null ? null : new java.util.LinkedHashMap<>(templateConfig);
        d.status = STATUS_OFFLINE;
        d.validateForSave();
        return d;
    }

    /** 从持久化层重建（不强制 eventTypeCodes 非空，兼容历史数据）。 */
    public static IndicatorDefinition rehydrate(Long id, Long groupId, String refName, String name, String description,
                                                List<String> eventTypeCodes, List<String> dimensions,
                                                int windowDays, SliceGranularity sliceGranularity,
                                                String accScript, String defaultValueStrategy, String status,
                                                String templateType, java.util.Map<String, Object> templateConfig) {
        IndicatorDefinition d = new IndicatorDefinition();
        d.id = id;
        d.groupId = groupId;
        d.refName = refName;
        d.name = name;
        d.description = description;
        d.eventTypeCodes = eventTypeCodes == null ? List.of() : new ArrayList<>(eventTypeCodes);
        d.dimensions = dimensions;
        d.windowDays = windowDays;
        d.sliceGranularity = sliceGranularity;
        d.accScript = accScript;
        d.defaultValueStrategy = defaultValueStrategy;
        d.status = status == null || status.isBlank() ? STATUS_OFFLINE : status;
        d.templateType = templateType;
        d.templateConfig = templateConfig == null ? null : new java.util.LinkedHashMap<>(templateConfig);
        d.validateInvariants();
        return d;
    }

    public void validateForSave() {
        validateInvariants();
        ValidationException.Builder errors = ValidationException.builder();
        if (eventTypeCodes == null || eventTypeCodes.isEmpty()) {
            errors.field("eventTypeCodes", "至少绑定一个事件");
        }
        errors.throwIfAny();
    }

    private void validateInvariants() {
        ValidationException.Builder errors = ValidationException.builder();
        if (refName == null || refName.isEmpty()) {
            errors.field("refName", "必填");
        } else if (refName.length() > REF_NAME_MAX) {
            errors.field("refName", "长度不能超过 " + REF_NAME_MAX);
        } else if (!REF_NAME_PATTERN.matcher(refName).matches()) {
            errors.field("refName", "只能包含字母、数字与下划线");
        }
        if (name != null && name.length() > NAME_MAX) {
            errors.field("name", "长度不能超过 " + NAME_MAX);
        }
        if (dimensions == null || dimensions.isEmpty()) {
            errors.field("dimensions", "必填");
        }
        if (sliceGranularity == null) {
            errors.field("sliceGranularity", "必填");
        }
        if (accScript == null || accScript.isEmpty()) {
            errors.field("accScript", "必填");
        }
        if (windowDays < WINDOW_DAYS_MIN || windowDays > WINDOW_DAYS_MAX) {
            errors.field("windowDays", "须在 [" + WINDOW_DAYS_MIN + "," + WINDOW_DAYS_MAX + "]");
        }
        errors.throwIfAny();
    }

    public void assignId(Long id) {
        this.id = id;
    }

    /** 更新可变属性并重新校验不变式（保留 id 与 refName）。 */
    public void update(Long groupId, String name, String description, List<String> eventTypeCodes,
                       List<String> dimensions, int windowDays, SliceGranularity sliceGranularity,
                       String accScript, String defaultValueStrategy,
                       String templateType, java.util.Map<String, Object> templateConfig) {
        this.groupId = groupId;
        this.name = name;
        this.description = description;
        this.eventTypeCodes = eventTypeCodes == null ? List.of() : new ArrayList<>(eventTypeCodes);
        this.dimensions = dimensions;
        this.windowDays = windowDays;
        this.sliceGranularity = sliceGranularity;
        this.accScript = accScript;
        this.defaultValueStrategy = defaultValueStrategy;
        this.templateType = templateType;
        this.templateConfig = templateConfig == null ? null : new java.util.LinkedHashMap<>(templateConfig);
        validateForSave();
    }

    public void online() {
        if (eventTypeCodes == null || eventTypeCodes.isEmpty()) {
            ValidationException.builder()
                    .field("eventTypeCodes", "上线前须至少绑定一个事件")
                    .throwIfAny();
        }
        this.status = STATUS_ONLINE;
    }

    public void offline() {
        this.status = STATUS_OFFLINE;
    }

    public boolean isOnline() {
        return STATUS_ONLINE.equals(status);
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getRefName() {
        return refName;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getEventTypeCodes() {
        return eventTypeCodes == null ? List.of() : List.copyOf(eventTypeCodes);
    }

    public List<String> getDimensions() {
        return dimensions;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public SliceGranularity getSliceGranularity() {
        return sliceGranularity;
    }

    public String getAccScript() {
        return accScript;
    }

    public String getDefaultValueStrategy() {
        return defaultValueStrategy;
    }

    public String getStatus() {
        return status;
    }

    public String getTemplateType() {
        return templateType;
    }

    public java.util.Map<String, Object> getTemplateConfig() {
        return templateConfig == null ? null : java.util.Map.copyOf(templateConfig);
    }
}
