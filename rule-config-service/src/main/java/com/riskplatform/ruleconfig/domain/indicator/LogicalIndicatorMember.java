package com.riskplatform.ruleconfig.domain.indicator;

/** 逻辑指标成员：指向一个物理指标的 refName。 */
public record LogicalIndicatorMember(String memberRefName, String eventTypeCode, int sortOrder) {
}
