package com.riskplatform.ruleconfig.application.indicator;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.ruleconfig.application.audit.Audited;
import com.riskplatform.ruleconfig.domain.audit.AuditOpType;
import com.riskplatform.ruleconfig.domain.audit.AuditTargetType;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.indicator.CombineMode;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinition;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionRepository;
import com.riskplatform.ruleconfig.domain.indicator.LogicalIndicator;
import com.riskplatform.ruleconfig.domain.indicator.LogicalIndicatorMember;
import com.riskplatform.ruleconfig.domain.indicator.LogicalIndicatorRepository;
import com.riskplatform.ruleconfig.domain.indicator.SliceGranularity;
import com.riskplatform.ruleconfig.domain.rule.ExpressionValidationResult;
import com.riskplatform.ruleconfig.domain.rule.ExpressionValidator;
import com.riskplatform.ruleconfig.infrastructure.indicator.IndicatorReferenceChecker;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 逻辑指标应用服务（方案 C：虚拟 refName + 物理成员）。 */
public class LogicalIndicatorAppService {

    private final LogicalIndicatorRepository logicalRepository;
    private final IndicatorDefinitionRepository physicalRepository;
    private final ExpressionValidator expressionValidator;
    private final ConfigChangePublisher configChangePublisher;
    private final IndicatorReferenceChecker referenceChecker;

    public LogicalIndicatorAppService(LogicalIndicatorRepository logicalRepository,
                                      IndicatorDefinitionRepository physicalRepository,
                                      ExpressionValidator expressionValidator,
                                      ConfigChangePublisher configChangePublisher,
                                      IndicatorReferenceChecker referenceChecker) {
        this.logicalRepository = logicalRepository;
        this.physicalRepository = physicalRepository;
        this.expressionValidator = expressionValidator;
        this.configChangePublisher = configChangePublisher;
        this.referenceChecker = referenceChecker;
    }

    @Audited(target = AuditTargetType.INDICATOR, op = AuditOpType.CREATE)
    public LogicalIndicator create(Long groupId, String refName, String name, String description,
                                   CombineMode combineMode, String combineExpression,
                                   List<String> dimensions, int windowDays, SliceGranularity granularity,
                                   String defaultValueStrategy, List<LogicalIndicatorMember> members) {
        assertRefNameAvailable(refName);
        validateMembers(members, dimensions, windowDays, granularity);
        validateCombineExpression(combineMode, combineExpression, members);
        LogicalIndicator li = LogicalIndicator.create(
                groupId, refName, name, description, combineMode, combineExpression,
                dimensions, windowDays, granularity, defaultValueStrategy, members);
        LogicalIndicator saved = logicalRepository.save(li, members);
        publishChange(refName);
        return saved;
    }

    public List<LogicalIndicator> list(Long groupId, Boolean ungroupedOnly, String status) {
        return logicalRepository.findAll(groupId, ungroupedOnly, status);
    }

    @Audited(target = AuditTargetType.INDICATOR, op = AuditOpType.UPDATE)
    public LogicalIndicator update(Long id, Long groupId, String name, String description,
                                   CombineMode combineMode, String combineExpression,
                                   List<String> dimensions, int windowDays, SliceGranularity granularity,
                                   String defaultValueStrategy, List<LogicalIndicatorMember> members) {
        LogicalIndicator existing = logicalRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("逻辑指标不存在: id=" + id));
        validateMembers(members, dimensions, windowDays, granularity);
        validateCombineExpression(combineMode, combineExpression, members);
        existing.update(groupId, name, description, combineMode, combineExpression,
                dimensions, windowDays, granularity, defaultValueStrategy, members);
        LogicalIndicator saved = logicalRepository.update(existing, members);
        publishChange(existing.getRefName());
        return saved;
    }

    @Audited(target = AuditTargetType.INDICATOR, op = AuditOpType.UPDATE)
    public LogicalIndicator online(Long id) {
        LogicalIndicator li = require(id);
        li.online();
        LogicalIndicator saved = logicalRepository.update(li, li.getMembers());
        publishChange(li.getRefName());
        return saved;
    }

    @Audited(target = AuditTargetType.INDICATOR, op = AuditOpType.UPDATE)
    public LogicalIndicator offline(Long id) {
        LogicalIndicator li = require(id);
        assertNotReferenced(li.getRefName(), "下线");
        li.offline();
        LogicalIndicator saved = logicalRepository.update(li, li.getMembers());
        publishChange(li.getRefName());
        return saved;
    }

    @Audited(target = AuditTargetType.INDICATOR, op = AuditOpType.DELETE)
    public void delete(Long id) {
        LogicalIndicator li = require(id);
        assertNotReferenced(li.getRefName(), "删除");
        logicalRepository.deleteById(id);
        publishChange(li.getRefName());
    }

    public List<String> findReferences(String refName) {
        return referenceChecker.findReferences(refName);
    }

    public List<Long> findReferencingRules(String refName) {
        return physicalRepository.findReferencingRuleIds(refName);
    }

    private void assertNotReferenced(String refName, String action) {
        List<String> refs = referenceChecker.findReferences(refName);
        if (!refs.isEmpty()) {
            throw new BizException(CommonErrorCode.INVALID_FIELD,
                    "指标正被引用，无法" + action + "：" + String.join("、", refs));
        }
    }

    private LogicalIndicator require(Long id) {
        return logicalRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("逻辑指标不存在: id=" + id));
    }

    private void assertRefNameAvailable(String refName) {
        if (logicalRepository.existsByRefName(refName) || physicalRepository.existsByRefName(refName)) {
            throw BizException.duplicate("指标引用名已存在: " + refName);
        }
    }

    private void validateMembers(List<LogicalIndicatorMember> members, List<String> dimensions,
                                 int windowDays, SliceGranularity granularity) {
        Set<String> seen = new HashSet<>();
        for (LogicalIndicatorMember member : members) {
            if (!seen.add(member.memberRefName())) {
                throw new BizException(CommonErrorCode.INVALID_FIELD,
                        "成员 refName 重复: " + member.memberRefName());
            }
            IndicatorDefinition physical = physicalRepository.findByRefName(member.memberRefName())
                    .orElseThrow(() -> BizException.notFound("物理指标不存在: " + member.memberRefName()));
            if (!Objects.equals(physical.getDimensions(), dimensions)) {
                throw new BizException(CommonErrorCode.INVALID_FIELD,
                        "成员 " + member.memberRefName() + " 的统计维度与逻辑指标不一致");
            }
            if (physical.getWindowDays() != windowDays) {
                throw new BizException(CommonErrorCode.INVALID_FIELD,
                        "成员 " + member.memberRefName() + " 的时间窗口与逻辑指标不一致");
            }
            if (physical.getSliceGranularity() != granularity) {
                throw new BizException(CommonErrorCode.INVALID_FIELD,
                        "成员 " + member.memberRefName() + " 的切片粒度与逻辑指标不一致");
            }
        }
    }

    private void validateCombineExpression(CombineMode mode, String expression,
                                           List<LogicalIndicatorMember> members) {
        if (mode != CombineMode.EXPRESSION) {
            return;
        }
        Set<String> vars = members.stream().map(LogicalIndicatorMember::memberRefName).collect(Collectors.toSet());
        ExpressionValidationResult result = expressionValidator.validate(expression, vars);
        if (!result.valid() && result.syntaxError() != null) {
            throw new BizException(CommonErrorCode.INVALID_FIELD,
                    "组合表达式语法错误: " + result.syntaxError());
        }
    }

    private void publishChange(String refName) {
        configChangePublisher.publishChange("LOGICAL_INDICATOR", refName);
    }
}
