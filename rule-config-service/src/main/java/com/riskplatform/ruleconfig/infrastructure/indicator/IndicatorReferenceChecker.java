package com.riskplatform.ruleconfig.infrastructure.indicator;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 指标引用扫描（IR1）：在规则包条件 / 决策流节点 JSON 中按 refName 匹配。
 */
@Component
public class IndicatorReferenceChecker {

    private final IndicatorReferenceMapper referenceMapper;

    public IndicatorReferenceChecker(IndicatorReferenceMapper referenceMapper) {
        this.referenceMapper = referenceMapper;
    }

    /** @return 可读引用描述，如「规则包:PKG_X/R001」「决策流:FLOW_Y」 */
    public List<String> findReferences(String refName) {
        if (!StringUtils.hasText(refName)) {
            return List.of();
        }
        Set<String> refs = new LinkedHashSet<>();
        String pattern = "%" + refName + "%";
        safeAddAll(refs, () -> referenceMapper.findRulePackageReferences(pattern));
        safeAddAll(refs, () -> referenceMapper.findDecisionFlowReferences(pattern));
        safeAddAll(refs, () -> referenceMapper.findDecisionFlowVersionReferences(pattern));
        safeAddAll(refs, () -> referenceMapper.findLogicalIndicatorMemberReferences(refName));
        safeAddAll(refs, () -> referenceMapper.findLogicalIndicatorExpressionReferences(pattern));
        refs.removeIf(s -> s == null || s.isBlank());
        return new ArrayList<>(refs);
    }

    private void safeAddAll(Set<String> refs, java.util.function.Supplier<List<String>> query) {
        try {
            refs.addAll(query.get());
        } catch (DataAccessException ignored) {
            // 单源查询失败不阻断其它引用源（IR1）
        }
    }

    public boolean isReferenced(String refName) {
        return !findReferences(refName).isEmpty();
    }
}
