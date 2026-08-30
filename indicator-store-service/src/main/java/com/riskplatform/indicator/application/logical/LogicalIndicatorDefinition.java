package com.riskplatform.indicator.application.logical;

import java.util.List;

/** 逻辑指标读取定义（从 rule-config 同步，仅上线条目参与虚拟 ref 解析）。 */
public record LogicalIndicatorDefinition(
        String refName,
        String combineMode,
        String combineExpression,
        List<String> memberRefNames) {
}
