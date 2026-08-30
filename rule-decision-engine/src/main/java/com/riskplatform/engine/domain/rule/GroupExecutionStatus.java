package com.riskplatform.engine.domain.rule;

/**
 * 规则组执行状态（R5.4）。
 */
public enum GroupExecutionStatus {
    /** 正常完成（含短路提前结束） */
    COMPLETED,
    /** 致命错误导致中断（保留已产出命中结果） */
    INTERRUPTED
}
