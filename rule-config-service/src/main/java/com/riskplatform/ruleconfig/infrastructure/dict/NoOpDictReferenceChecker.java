package com.riskplatform.ruleconfig.infrastructure.dict;

import com.riskplatform.ruleconfig.domain.dict.DictReferenceChecker;
import org.springframework.stereotype.Component;

/**
 * 字典/枚举引用校验默认实现（R12.4）。
 *
 * <p>本阶段（阶段三支撑维度）规则/规则包等引用方尚未建立，统一返回「无引用」，
 * 即无引用直接删除。后续阶段建立 rule_v2/rule_package 等引用方后，可新增更高优先级实现
 * （或替换本 Bean）查询真实引用关系，本类作为兜底默认值，避免编辑共享配置类。
 */
@Component
public class NoOpDictReferenceChecker implements DictReferenceChecker {

    @Override
    public boolean isRiskTypeReferenced(String code) {
        return false;
    }

    @Override
    public boolean isRiskLevelReferenced(String code) {
        return false;
    }

    @Override
    public boolean isDecisionTagReferenced(String code) {
        return false;
    }

    @Override
    public boolean isEnumLibReferenced(String code) {
        return false;
    }

    @Override
    public boolean isEnumValueReferenced(String enumLibCode, String value) {
        return false;
    }
}
