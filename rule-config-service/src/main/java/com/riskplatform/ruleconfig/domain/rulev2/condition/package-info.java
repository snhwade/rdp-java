/**
 * rulev2 子域 —— 结构化规则条件树领域模型（R2）。
 *
 * <p>包含条件树值对象（AND/OR/NOT/LEAF 节点）、左变量、运算符（含数据类型适配校验）、
 * 右值与条件树 JSON 序列化/反序列化，对齐 design.md 的 {@code condition_json} 结构。
 *
 * <p>编译为 Aviator 表达式（ConditionCompiler，任务 4.3）与应用服务（任务 4.4）在后续任务实现。
 */
package com.riskplatform.ruleconfig.domain.rulev2.condition;
