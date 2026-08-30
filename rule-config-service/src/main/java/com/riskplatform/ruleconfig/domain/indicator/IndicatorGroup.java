package com.riskplatform.ruleconfig.domain.indicator;

import com.riskplatform.common.error.ValidationException;

import java.util.ArrayList;
import java.util.List;

/** 指标分组：可绑定多个事件，其下管理多个指标定义。 */
public class IndicatorGroup {

    public static final int NAME_MAX = 128;
    public static final int ORG_NAME_MAX = 64;

    private Long id;
    private String name;
    private String orgName;
    private List<String> eventTypeCodes;
    private String description;

    private IndicatorGroup() {
    }

    public static IndicatorGroup create(String name, String orgName, List<String> eventTypeCodes, String description) {
        IndicatorGroup g = new IndicatorGroup();
        g.name = name;
        g.orgName = orgName == null || orgName.isBlank() ? "总部" : orgName.trim();
        g.eventTypeCodes = eventTypeCodes == null ? List.of() : new ArrayList<>(eventTypeCodes);
        g.description = description;
        g.validateForSave();
        return g;
    }

    public static IndicatorGroup rehydrate(Long id, String name, String orgName,
                                           List<String> eventTypeCodes, String description) {
        IndicatorGroup g = new IndicatorGroup();
        g.id = id;
        g.name = name;
        g.orgName = orgName == null || orgName.isBlank() ? "总部" : orgName;
        g.eventTypeCodes = eventTypeCodes == null ? List.of() : new ArrayList<>(eventTypeCodes);
        g.description = description;
        g.validateInvariants();
        return g;
    }

    public void update(String name, String orgName, List<String> eventTypeCodes, String description) {
        this.name = name;
        this.orgName = orgName == null || orgName.isBlank() ? "总部" : orgName.trim();
        this.eventTypeCodes = eventTypeCodes == null ? List.of() : new ArrayList<>(eventTypeCodes);
        this.description = description;
        validateForSave();
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
        if (name == null || name.isBlank()) {
            errors.field("name", "必填");
        } else if (name.length() > NAME_MAX) {
            errors.field("name", "长度不能超过 " + NAME_MAX);
        }
        if (orgName != null && orgName.length() > ORG_NAME_MAX) {
            errors.field("orgName", "长度不能超过 " + ORG_NAME_MAX);
        }
        errors.throwIfAny();
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOrgName() {
        return orgName;
    }

    public List<String> getEventTypeCodes() {
        return eventTypeCodes == null ? List.of() : List.copyOf(eventTypeCodes);
    }

    public String getDescription() {
        return description;
    }
}
