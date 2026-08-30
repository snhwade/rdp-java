package com.riskplatform.ruleconfig.domain.rulev2.condition;

/**
 * 左变量来源（R2.1）。
 *
 * <p>决定引擎在编译/执行期如何解析该变量的取值表达式。
 */
public enum VariableSource {
    /** 事件字段（直接按字段名取值）。 */
    FIELD,
    /** 指标（按指标注入名取值）。 */
    INDICATOR,
    /** 模型输出（按模型赋值上下文键取值）。 */
    MODEL,
    /** 赋值字段（决策流中前序节点产出的赋值字段）。 */
    ASSIGNMENT
}
