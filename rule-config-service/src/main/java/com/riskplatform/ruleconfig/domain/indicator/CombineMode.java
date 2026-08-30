package com.riskplatform.ruleconfig.domain.indicator;

import java.util.List;

/** 逻辑指标组合方式。 */
public enum CombineMode {
    /** 成员值求和（缺失成员按 0 计）。 */
    SUM,
    /** 自定义 Aviator 表达式，变量名为成员 refName。 */
    EXPRESSION
}
