package com.riskplatform.ruleconfig.domain.dict;

/**
 * 字典/枚举项引用校验钩子（R12.4）。
 *
 * <p>删除被引用的字典项/枚举值前，应用层通过本端口检查引用关系：存在引用则拒绝删除。
 *
 * <p>本阶段（阶段三支撑维度）引用方（规则/规则包等，rule_v2/rule_package）尚未建立，
 * 故默认实现 {@link com.riskplatform.ruleconfig.infrastructure.dict.NoOpDictReferenceChecker}
 * 返回「无引用」，即无引用直接删除。后续阶段一/二建立引用方后，可新增实现类查询真实引用关系，
 * 无需改动应用层与 REST 层。
 */
public interface DictReferenceChecker {

    /** 风险类型是否被引用。 */
    boolean isRiskTypeReferenced(String code);

    /** 风险等级是否被引用。 */
    boolean isRiskLevelReferenced(String code);

    /** 决策标签是否被引用。 */
    boolean isDecisionTagReferenced(String code);

    /** 枚举库是否被引用。 */
    boolean isEnumLibReferenced(String code);

    /** 枚举值是否被引用（按枚举库编码 + 值）。 */
    boolean isEnumValueReferenced(String enumLibCode, String value);
}
