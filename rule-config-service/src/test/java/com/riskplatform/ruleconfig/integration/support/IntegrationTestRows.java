package com.riskplatform.ruleconfig.integration.support;

import java.math.BigDecimal;

/** 集成测试 MyBatis 查询行映射。 */
public final class IntegrationTestRows {

    private IntegrationTestRows() {
    }

    public static class StrategyDefRow {
        private String category;
        private Integer priority;
        private Long scopeScenarioId;
        private Integer anyScope;
        private String name;

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        public Long getScopeScenarioId() { return scopeScenarioId; }
        public void setScopeScenarioId(Long scopeScenarioId) { this.scopeScenarioId = scopeScenarioId; }
        public Integer getAnyScope() { return anyScope; }
        public void setAnyScope(Integer anyScope) { this.anyScope = anyScope; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class RulePackageRow {
        private String code;
        private String name;
        private String triggerMode;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getTriggerMode() { return triggerMode; }
        public void setTriggerMode(String triggerMode) { this.triggerMode = triggerMode; }
    }

    public static class DecisionFlowRow {
        private String name;
        private String eventTypeCode;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEventTypeCode() { return eventTypeCode; }
        public void setEventTypeCode(String eventTypeCode) { this.eventTypeCode = eventTypeCode; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class DecisionFlowVersionRow {
        private Integer version;
        private String status;
        private String snapshotJson;

        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getSnapshotJson() { return snapshotJson; }
        public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    }

    public static class RatingModelRow {
        private String name;
        private String gradingMode;
        private String executionMode;
        private String subject;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getGradingMode() { return gradingMode; }
        public void setGradingMode(String gradingMode) { this.gradingMode = gradingMode; }
        public String getExecutionMode() { return executionMode; }
        public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class RatingModelVersionRow {
        private Integer version;
        private String status;
        private Integer currentVersion;

        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(Integer currentVersion) { this.currentVersion = currentVersion; }
    }

    public static class RatingGradeBandRow {
        private BigDecimal minScore;
        private BigDecimal maxScore;
        private String grade;
        private Integer orderNo;

        public BigDecimal getMinScore() { return minScore; }
        public void setMinScore(BigDecimal minScore) { this.minScore = minScore; }
        public BigDecimal getMaxScore() { return maxScore; }
        public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }
        public Integer getOrderNo() { return orderNo; }
        public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
    }

    public static class ColumnMetaRow {
        private String columnName;
        private String columnType;
        private String isNullable;
        private String columnKey;

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getColumnType() { return columnType; }
        public void setColumnType(String columnType) { this.columnType = columnType; }
        public String getIsNullable() { return isNullable; }
        public void setIsNullable(String isNullable) { this.isNullable = isNullable; }
        public String getColumnKey() { return columnKey; }
        public void setColumnKey(String columnKey) { this.columnKey = columnKey; }
    }

    public static class DuplicateKeyRow {
        private String k;
        private Long c;

        public String getK() { return k; }
        public void setK(String k) { this.k = k; }
        public Long getC() { return c; }
        public void setC(Long c) { this.c = c; }
    }

    public static class DecisionFlowDetailRow {
        private String name;
        private String eventTypeCode;
        private String startNodeId;
        private String nodesJson;
        private String edgesJson;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEventTypeCode() { return eventTypeCode; }
        public void setEventTypeCode(String eventTypeCode) { this.eventTypeCode = eventTypeCode; }
        public String getStartNodeId() { return startNodeId; }
        public void setStartNodeId(String startNodeId) { this.startNodeId = startNodeId; }
        public String getNodesJson() { return nodesJson; }
        public void setNodesJson(String nodesJson) { this.nodesJson = nodesJson; }
        public String getEdgesJson() { return edgesJson; }
        public void setEdgesJson(String edgesJson) { this.edgesJson = edgesJson; }
    }

    public static class RatingModelDetailRow {
        private String name;
        private String eventTypeCode;
        private String executionMode;
        private String subject;
        private String gradingMode;
        private String status;
        private Integer version;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEventTypeCode() { return eventTypeCode; }
        public void setEventTypeCode(String eventTypeCode) { this.eventTypeCode = eventTypeCode; }
        public String getExecutionMode() { return executionMode; }
        public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getGradingMode() { return gradingMode; }
        public void setGradingMode(String gradingMode) { this.gradingMode = gradingMode; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
    }

    public static class VersionSnapshotRow {
        private Integer version;
        private String snapshotJson;

        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public String getSnapshotJson() { return snapshotJson; }
        public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    }
}
