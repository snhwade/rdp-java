package com.riskplatform.ruleconfig.application.indicator;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.application.audit.Audited;
import com.riskplatform.ruleconfig.domain.audit.AuditOpType;
import com.riskplatform.ruleconfig.domain.audit.AuditTargetType;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.eventtype.EventTypeRepository;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinition;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionRepository;
import com.riskplatform.ruleconfig.domain.indicator.LogicalIndicatorRepository;
import com.riskplatform.ruleconfig.domain.indicator.SliceGranularity;
import com.riskplatform.ruleconfig.domain.rule.ExpressionValidationResult;
import com.riskplatform.ruleconfig.domain.rule.ExpressionValidator;
import com.riskplatform.ruleconfig.infrastructure.indicator.IndicatorReferenceChecker;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 指标定义应用服务（R7）。
 *
 * <p>创建前校验：领域不变式（refName 格式/窗口/维度）、refName 唯一（R7.3）、
 * accScript 语法（R7.4，复用 Aviator 校验器）、绑定事件存在性。
 * 提供引用关系查询（R7.6/IR1）供前端更新前提示；删除/下线有引用时阻断（IR1）。
 */
public class IndicatorDefinitionAppService {

    private final IndicatorDefinitionRepository repository;
    private final LogicalIndicatorRepository logicalRepository;
    private final EventTypeRepository eventTypeRepository;
    private final ExpressionValidator expressionValidator;
    private final ConfigChangePublisher configChangePublisher;
    private final IndicatorReferenceChecker referenceChecker;
    private final IndicatorDefinitionSnapshotAppService snapshotAppService;

    public IndicatorDefinitionAppService(IndicatorDefinitionRepository repository,
                                         LogicalIndicatorRepository logicalRepository,
                                         EventTypeRepository eventTypeRepository,
                                         ExpressionValidator expressionValidator,
                                         ConfigChangePublisher configChangePublisher,
                                         IndicatorReferenceChecker referenceChecker,
                                         IndicatorDefinitionSnapshotAppService snapshotAppService) {
        this.repository = repository;
        this.logicalRepository = logicalRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.expressionValidator = expressionValidator;
        this.configChangePublisher = configChangePublisher;
        this.referenceChecker = referenceChecker;
        this.snapshotAppService = snapshotAppService;
    }

    /** 创建指标定义（R7.1/R7.2/R7.3/R7.4/R7.5）。 */
    @Audited(target = AuditTargetType.INDICATOR, op = AuditOpType.CREATE)
    public IndicatorDefinition create(Long groupId, String refName, String name, String description,
                                      List<String> eventTypeCodes, List<String> dimensions,
                                      int windowDays, SliceGranularity granularity,
                                      String accScript, String defaultValueStrategy,
                                      String templateType, java.util.Map<String, Object> templateConfig) {
        validateEventTypeCodes(eventTypeCodes);
        IndicatorDefinition def = IndicatorDefinition.create(
                groupId, refName, name, description, eventTypeCodes, dimensions, windowDays,
                granularity, accScript, defaultValueStrategy, templateType, templateConfig);
        if (repository.existsByRefName(refName) || logicalRepository.existsByRefName(refName)) {
            throw BizException.duplicate("指标引用名已存在: " + refName);
        }
        validateAccScript(accScript, dimensions);
        IndicatorDefinition saved = repository.save(def);
        configChangePublisher.publishChange("INDICATOR", refName);
        return saved;
    }

    /** 查询引用该指标的规则包/决策流/逻辑指标描述列表（IR1）。 */
    public List<String> findReferences(String refName) {
        return referenceChecker.findReferences(refName);
    }

    /** @deprecated 保留兼容；请使用 {@link #findReferences(String)} */
    public List<Long> findReferencingRules(String refName) {
        return repository.findReferencingRuleIds(refName);
    }

    /** 列出指标定义，可按分组、事件与状态筛选。 */
    public List<IndicatorDefinition> list(Long groupId, Boolean ungroupedOnly,
                                          String eventTypeCode, String status) {
        return repository.findAll(groupId, ungroupedOnly, eventTypeCode, status);
    }

    /** 更新指标定义（按 id）。refName 不可变更；校验累计脚本语法后保存并广播变更。 */
    @Audited(target = AuditTargetType.INDICATOR, op = AuditOpType.UPDATE)
    public IndicatorDefinition update(Long id, Long groupId, String name, String description,
                                      List<String> eventTypeCodes, List<String> dimensions,
                                      int windowDays, SliceGranularity granularity,
                                      String accScript, String defaultValueStrategy,
                                      String templateType, java.util.Map<String, Object> templateConfig) {
        validateEventTypeCodes(eventTypeCodes);
        IndicatorDefinition def = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("指标定义不存在: id=" + id));
        validateAccScript(accScript, dimensions);
        snapshotAppService.captureBeforeUpdate(def);
        def.update(groupId, name, description, eventTypeCodes, dimensions, windowDays, granularity,
                accScript, defaultValueStrategy, templateType, templateConfig);
        IndicatorDefinition saved = repository.update(def);
        configChangePublisher.publishChange("INDICATOR", def.getRefName());
        return saved;
    }

    /** 指标上线：仅上线指标参与累计。 */
    @Audited(target = AuditTargetType.INDICATOR, op = AuditOpType.UPDATE)
    public IndicatorDefinition online(Long id) {
        IndicatorDefinition def = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("指标定义不存在: id=" + id));
        def.online();
        IndicatorDefinition saved = repository.update(def);
        configChangePublisher.publishChange("INDICATOR", def.getRefName());
        return saved;
    }

    /** 指标下线：停止累计；有引用时阻断（IR1）。 */
    @Audited(target = AuditTargetType.INDICATOR, op = AuditOpType.UPDATE)
    public IndicatorDefinition offline(Long id) {
        IndicatorDefinition def = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("指标定义不存在: id=" + id));
        assertNotReferenced(def.getRefName(), "下线");
        def.offline();
        IndicatorDefinition saved = repository.update(def);
        configChangePublisher.publishChange("INDICATOR", def.getRefName());
        return saved;
    }

    /** 删除指标定义（按 id）；有引用时阻断（IR1）。 */
    @Audited(target = AuditTargetType.INDICATOR, op = AuditOpType.DELETE)
    public void delete(Long id) {
        IndicatorDefinition def = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("指标定义不存在: id=" + id));
        assertNotReferenced(def.getRefName(), "删除");
        repository.deleteById(id);
        configChangePublisher.publishChange("INDICATOR", def.getRefName());
    }

    private void assertNotReferenced(String refName, String action) {
        List<String> refs = referenceChecker.findReferences(refName);
        if (!refs.isEmpty()) {
            throw new BizException(com.riskplatform.common.error.CommonErrorCode.INVALID_FIELD,
                    "指标正被引用，无法" + action + "：" + String.join("、", refs));
        }
    }

    private void validateEventTypeCodes(List<String> eventTypeCodes) {
        if (eventTypeCodes == null || eventTypeCodes.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String code : eventTypeCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            if (!seen.add(code)) {
                continue;
            }
            eventTypeRepository.findByCode(code)
                    .orElseThrow(() -> BizException.notFound("事件类型不存在: " + code));
        }
    }

    private void validateAccScript(String accScript, List<String> dimensions) {
        ExpressionValidationResult result = expressionValidator.validate(accScript, Set.copyOf(dimensions));
        if (!result.valid() && result.syntaxError() != null) {
            throw new BizException(com.riskplatform.common.error.CommonErrorCode.INVALID_FIELD,
                    "累计脚本语法错误: " + result.syntaxError());
        }
    }
}
