package com.riskplatform.ruleconfig.domain.rulev2;

import java.math.BigDecimal;

/**
 * 规则列表读模型（R6.4）。
 *
 * <p>用于规则包详情的规则列表展示，仅承载列表所需字段：规则编码、名称、状态、决策事件、
 * 风险等级、风险分值。状态以字符串承载（ONLINE/TRIAL_RUN/OFFLINE），不经状态枚举转换，
 * 以便在并行引入三态枚举期间仍能准确反映库内三态值。
 *
 * @param id            规则主键
 * @param code          规则编码
 * @param name          规则名称
 * @param status        规则状态字符串（ONLINE/TRIAL_RUN/OFFLINE，历史值 ENABLED/DISABLED 亦原样透出）
 * @param decisionEventCode 决策事件类型编码
 * @param riskLevelCode 风险等级编码
 * @param riskScore     风险分值（评分规则的基础分；命中规则可为空）
 * @param remark        备注（人工说明，可选）
 */
public record RuleListItem(Long id, String code, String name, String status,
                           String decisionEventCode, String riskLevelCode, BigDecimal riskScore,
                           String remark) {
}
