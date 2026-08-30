package com.riskplatform.ruleconfig.domain.indicator;

import com.riskplatform.common.error.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 逻辑指标聚合根（方案 C）。
 *
 * <p>产品层一个虚拟 refName；运行时由多个物理指标成员在 indicator-store 读取层聚合。
 * 不参与 Kafka 累计，仅提供规则读取入口。
 */
public class LogicalIndicator {

    public static final int REF_NAME_MAX = 64;
    private static final Pattern REF_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private Long groupId;
    private String refName;
    private String name;
    private String description;
    private CombineMode combineMode;
    private String combineExpression;
    private List<String> dimensions;
    private int windowDays;
    private SliceGranularity sliceGranularity;
    private String defaultValueStrategy;
    private String status;
    private List<LogicalIndicatorMember> members;

    private LogicalIndicator() {
    }

    public static LogicalIndicator create(Long groupId, String refName, String name, String description,
                                          CombineMode combineMode, String combineExpression,
                                          List<String> dimensions, int windowDays,
                                          SliceGranularity sliceGranularity, String defaultValueStrategy,
                                          List<LogicalIndicatorMember> members) {
        LogicalIndicator li = new LogicalIndicator();
        li.groupId = groupId;
        li.refName = refName;
        li.name = name;
        li.description = description;
        li.combineMode = combineMode == null ? CombineMode.SUM : combineMode;
        li.combineExpression = combineExpression;
        li.dimensions = dimensions == null ? List.of() : new ArrayList<>(dimensions);
        li.windowDays = windowDays;
        li.sliceGranularity = sliceGranularity;
        li.defaultValueStrategy = defaultValueStrategy;
        li.members = members == null ? List.of() : new ArrayList<>(members);
        li.status = IndicatorDefinition.STATUS_OFFLINE;
        li.validateForSave();
        return li;
    }

    public static LogicalIndicator rehydrate(Long id, Long groupId, String refName, String name, String description,
                                             CombineMode combineMode, String combineExpression,
                                             List<String> dimensions, int windowDays,
                                             SliceGranularity sliceGranularity, String defaultValueStrategy,
                                             String status, List<LogicalIndicatorMember> members) {
        LogicalIndicator li = new LogicalIndicator();
        li.id = id;
        li.groupId = groupId;
        li.refName = refName;
        li.name = name;
        li.description = description;
        li.combineMode = combineMode == null ? CombineMode.SUM : combineMode;
        li.combineExpression = combineExpression;
        li.dimensions = dimensions == null ? List.of() : new ArrayList<>(dimensions);
        li.windowDays = windowDays;
        li.sliceGranularity = sliceGranularity;
        li.defaultValueStrategy = defaultValueStrategy;
        li.status = status == null || status.isBlank() ? IndicatorDefinition.STATUS_OFFLINE : status;
        li.members = members == null ? List.of() : new ArrayList<>(members);
        li.validateInvariants();
        return li;
    }

    public void update(Long groupId, String name, String description, CombineMode combineMode,
                       String combineExpression, List<String> dimensions, int windowDays,
                       SliceGranularity sliceGranularity, String defaultValueStrategy,
                       List<LogicalIndicatorMember> members) {
        this.groupId = groupId;
        this.name = name;
        this.description = description;
        this.combineMode = combineMode == null ? CombineMode.SUM : combineMode;
        this.combineExpression = combineExpression;
        this.dimensions = dimensions == null ? List.of() : new ArrayList<>(dimensions);
        this.windowDays = windowDays;
        this.sliceGranularity = sliceGranularity;
        this.defaultValueStrategy = defaultValueStrategy;
        this.members = members == null ? List.of() : new ArrayList<>(members);
        validateForSave();
    }

    public void validateForSave() {
        validateInvariants();
        ValidationException.Builder errors = ValidationException.builder();
        if (members == null || members.isEmpty()) {
            errors.field("members", "至少添加一个物理指标成员");
        }
        if (combineMode == CombineMode.EXPRESSION
                && (combineExpression == null || combineExpression.isBlank())) {
            errors.field("combineExpression", "EXPRESSION 模式须填写组合表达式");
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
        if (dimensions == null || dimensions.isEmpty()) {
            errors.field("dimensions", "必填");
        }
        if (sliceGranularity == null) {
            errors.field("sliceGranularity", "必填");
        }
        if (windowDays < IndicatorDefinition.WINDOW_DAYS_MIN
                || windowDays > IndicatorDefinition.WINDOW_DAYS_MAX) {
            errors.field("windowDays", "须在 [" + IndicatorDefinition.WINDOW_DAYS_MIN
                    + "," + IndicatorDefinition.WINDOW_DAYS_MAX + "]");
        }
        errors.throwIfAny();
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public void online() {
        this.status = IndicatorDefinition.STATUS_ONLINE;
    }

    public void offline() {
        this.status = IndicatorDefinition.STATUS_OFFLINE;
    }

    public boolean isOnline() {
        return IndicatorDefinition.STATUS_ONLINE.equals(status);
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public String getRefName() { return refName; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CombineMode getCombineMode() { return combineMode; }
    public String getCombineExpression() { return combineExpression; }
    public List<String> getDimensions() { return dimensions == null ? List.of() : List.copyOf(dimensions); }
    public int getWindowDays() { return windowDays; }
    public SliceGranularity getSliceGranularity() { return sliceGranularity; }
    public String getDefaultValueStrategy() { return defaultValueStrategy; }
    public String getStatus() { return status; }
    public List<LogicalIndicatorMember> getMembers() {
        return members == null ? List.of() : List.copyOf(members);
    }
}
