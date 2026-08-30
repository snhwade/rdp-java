package com.riskplatform.ruleconfig.application.rulev2;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.ValidationException;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.field.FieldRepository;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionRepository;
import com.riskplatform.ruleconfig.domain.rulev2.DynamicScoreBand;
import com.riskplatform.ruleconfig.domain.rulev2.RuleKind;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2Repository;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2Status;
import com.riskplatform.ruleconfig.domain.rulev2.condition.ConditionCompiler;
import com.riskplatform.ruleconfig.domain.rulev2.condition.ConditionNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 结构化规则应用服务（R2.5/R2.6/R2.7）。
 *
 * <p>负责结构化规则的创建、更新、上下线、版本递增、编译校验与编译预览的编排：
 * <ul>
 *   <li><b>编译落库（R2.5/R14.2）：</b>创建/更新时用 {@link ConditionCompiler} 把条件树编译为
 *       Aviator 表达式，通过 {@link RuleV2#applyCompiled(String)} 写入 {@code compiled_expr} 并
 *       递增 {@code expr_version}。</li>
 *   <li><b>未声明校验（R2.7）：</b>编译时注入「已声明字段名 / 已声明指标名」集合，
 *       未声明的 FIELD/INDICATOR 引用由编译器抛出字段级 {@link ValidationException} 返回标识名。
 *       字段集合来自 {@link FieldRepository}，指标集合来自 {@link IndicatorDefinitionRepository}。</li>
 *   <li><b>条件重复校验（可配置开关）：</b>由配置 {@code rule-v2.duplicate-condition-check.enabled}
 *       控制（默认关闭）。开启时若已存在相同 {@code compiled_expr} 的其它规则，按
 *       {@code rule-v2.duplicate-condition-check.block}（默认 true=阻止）决定阻止保存或仅放行。
 *       本阶段以「编译表达式相等」作为重复判定，作为后续接入系统配置中心的钩子。</li>
 *   <li><b>编译预览（R2.5）：</b>{@link #compilePreview} 编译条件树并返回表达式与校验结果，不落库。</li>
 * </ul>
 *
 * <p>配置变更（上下线、保存）后通过 {@link ConfigChangePublisher} 广播，引擎 5 秒内生效。
 * 通过 {@code @Service} 组件扫描自注册，不改动共享装配类。
 */
@Service
public class RuleV2AppService {

    /** 配置变更类型标识（与引擎侧订阅约定一致）。 */
    private static final String CONFIG_TYPE = "RULE_V2";

    private final RuleV2Repository repository;
    private final FieldRepository fieldRepository;
    private final IndicatorDefinitionRepository indicatorRepository;
    private final ConfigChangePublisher configChangePublisher;

    /** 条件重复校验开关（默认关闭）。 */
    private final boolean duplicateCheckEnabled;
    /** 条件重复时是否阻止保存（默认 true=阻止；false=仅放行，可由调用方据需提示）。 */
    private final boolean duplicateCheckBlock;

    public RuleV2AppService(RuleV2Repository repository,
                            FieldRepository fieldRepository,
                            IndicatorDefinitionRepository indicatorRepository,
                            ConfigChangePublisher configChangePublisher,
                            @Value("${rule-v2.duplicate-condition-check.enabled:false}") boolean duplicateCheckEnabled,
                            @Value("${rule-v2.duplicate-condition-check.block:true}") boolean duplicateCheckBlock) {
        this.repository = repository;
        this.fieldRepository = fieldRepository;
        this.indicatorRepository = indicatorRepository;
        this.configChangePublisher = configChangePublisher;
        this.duplicateCheckEnabled = duplicateCheckEnabled;
        this.duplicateCheckBlock = duplicateCheckBlock;
    }

    /**
     * 创建结构化规则（R2.5/R2.7）。
     *
     * <p>构建聚合 → 替换动态分（评分规则）→ 编译条件树落库 → 条件重复校验 → 保存 → 广播。
     */
    public RuleV2 create(CreateCommand cmd) {
        RuleV2 rule = RuleV2.create(cmd.code(), cmd.name(), cmd.rulePackageId(), cmd.ruleKind(),
                cmd.eventTypeCode(), cmd.riskLevelCode(), cmd.riskTypeCode(), cmd.baseScore(),
                cmd.condition(), cmd.priority(), cmd.shortCircuited(), cmd.applicableOrgId(),
                cmd.includeSubOrg(), cmd.remark());
        if (cmd.ruleKind() == RuleKind.SCORE && cmd.dynamicScores() != null) {
            rule.replaceDynamicScores(cmd.dynamicScores());
        }
        compileAndApply(rule);
        checkDuplicateCondition(rule);
        RuleV2 saved = repository.save(rule);
        configChangePublisher.publishChange(CONFIG_TYPE, String.valueOf(saved.getId()));
        return saved;
    }

    /**
     * 更新结构化规则（R2.5/R2.7）。重新编译条件树（递增 expr_version）并递增规则版本。
     */
    public RuleV2 update(Long id, UpdateCommand cmd) {
        RuleV2 rule = require(id);
        rule.update(cmd.name(), cmd.eventTypeCode(), cmd.riskLevelCode(), cmd.riskTypeCode(),
                cmd.baseScore(), cmd.condition(), cmd.priority(), cmd.shortCircuited(),
                cmd.applicableOrgId(), cmd.includeSubOrg(), cmd.remark());
        if (rule.getRuleKind() == RuleKind.SCORE) {
            rule.replaceDynamicScores(cmd.dynamicScores());
        }
        compileAndApply(rule);
        checkDuplicateCondition(rule);
        rule.bumpVersion();
        repository.update(rule);
        configChangePublisher.publishChange(CONFIG_TYPE, String.valueOf(id));
        return rule;
    }

    /**
     * 切换结构化规则三态（R7.1/R7.2）：上线 / 试运行 / 下线。
     *
     * <p>持久化新状态并返回更新后的状态。状态变更后递增版本并广播配置变更，引擎 5 秒内生效。
     *
     * @param id     规则 id
     * @param status 目标状态（ONLINE / TRIAL_RUN / OFFLINE）
     * @return 更新后的规则聚合（其 {@code status} 为更新后的状态）
     */
    public RuleV2 changeStatus(Long id, RuleV2Status status) {
        if (status == null) {
            ValidationException.builder()
                    .field("status", "目标状态必填（ONLINE / TRIAL_RUN / OFFLINE）")
                    .throwIfAny();
        }
        RuleV2 rule = require(id);
        switch (status) {
            case ONLINE -> rule.online();
            case TRIAL_RUN -> rule.trialRun();
            case OFFLINE -> rule.offline();
        }
        rule.bumpVersion();
        repository.update(rule);
        configChangePublisher.publishChange(CONFIG_TYPE, String.valueOf(id));
        return rule;
    }

    /** 按 id 查询（含条件树与动态分）。 */
    public RuleV2 get(Long id) {
        return require(id);
    }

    /** 列表查询。 */
    public List<RuleV2> list() {
        return repository.findAll();
    }

    /**
     * 编译预览（R2.5）：编译条件树为 Aviator 表达式并返回校验结果，不落库。
     *
     * <p>成功返回表达式；失败时收集字段级错误（语法/未声明）原样返回，便于前端逐项回显并保留输入。
     *
     * @param condition 待编译的条件树
     * @return 预览结果（成功含表达式；失败含字段级错误映射）
     */
    public CompilePreviewResult compilePreview(ConditionNode condition) {
        try {
            String expr = ConditionCompiler.compile(condition, declaredFields(), declaredIndicators());
            return CompilePreviewResult.success(expr);
        } catch (ValidationException e) {
            return CompilePreviewResult.failure(e.getFields());
        }
    }

    // —— 内部辅助 ——

    /** 编译条件树并把产物写入聚合（递增 expr_version）。失败抛字段级 ValidationException。 */
    private void compileAndApply(RuleV2 rule) {
        String expr = ConditionCompiler.compile(rule.getCondition(), declaredFields(), declaredIndicators());
        rule.applyCompiled(expr);
    }

    /**
     * 条件重复校验（可配置开关）。开关关闭时直接跳过；开启且命中重复时按配置阻止或放行。
     */
    private void checkDuplicateCondition(RuleV2 rule) {
        if (!duplicateCheckEnabled) {
            return;
        }
        boolean duplicated = repository.existsByCompiledExpr(rule.getCompiledExpr(), rule.getId());
        if (duplicated && duplicateCheckBlock) {
            ValidationException.builder()
                    .field("condition", "已存在条件等价的规则（编译表达式重复）")
                    .throwIfAny();
        }
        // 放行模式（block=false）：不阻止保存，留作前端提示钩子，本阶段不额外处理
    }

    /**
     * 已声明字段名集合（R2.7），来自字段库。
     *
     * <p>同时纳入字段 code（英文标识，条件表达式/Aviator 实际引用）与 name（中文名），
     * 使前端按字段 code 引用的条件树可通过未声明校验。
     */
    private Set<String> declaredFields() {
        Set<String> declared = new java.util.HashSet<>();
        for (var f : fieldRepository.listFields()) {
            if (f.code() != null && !f.code().isBlank()) {
                declared.add(f.code());
            }
            if (f.name() != null && !f.name().isBlank()) {
                declared.add(f.name());
            }
        }
        return declared;
    }

    /** 已声明指标名集合（R2.7），来自指标定义。 */
    private Set<String> declaredIndicators() {
        return indicatorRepository.findAll(null, null, null, null).stream()
                .map(i -> i.getRefName())
                .collect(Collectors.toSet());
    }

    private RuleV2 require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("结构化规则不存在: " + id));
    }

    /**
     * 创建命令。条件树/规则类型等已由适配层解析为领域对象。
     */
    public record CreateCommand(String code, String name, Long rulePackageId, RuleKind ruleKind,
                                String eventTypeCode, String riskLevelCode, String riskTypeCode,
                                BigDecimal baseScore, ConditionNode condition, int priority,
                                boolean shortCircuited, Long applicableOrgId, boolean includeSubOrg,
                                String remark, List<DynamicScoreBand> dynamicScores) {
    }

    /**
     * 更新命令（不含 code 与 ruleKind：保持双轨稳定）。
     */
    public record UpdateCommand(String name, String eventTypeCode, String riskLevelCode, String riskTypeCode,
                                BigDecimal baseScore, ConditionNode condition, int priority,
                                boolean shortCircuited, Long applicableOrgId, boolean includeSubOrg,
                                String remark, List<DynamicScoreBand> dynamicScores) {
    }

    /**
     * 编译预览结果（R2.5）。
     *
     * @param success      是否编译成功
     * @param compiledExpr 编译后的 Aviator 表达式（成功时）
     * @param fieldErrors  字段级错误映射（失败时，字段名 → 错误描述）
     */
    public record CompilePreviewResult(boolean success, String compiledExpr,
                                       java.util.Map<String, String> fieldErrors) {
        static CompilePreviewResult success(String expr) {
            return new CompilePreviewResult(true, expr, null);
        }

        static CompilePreviewResult failure(java.util.Map<String, String> errors) {
            return new CompilePreviewResult(false, null, errors);
        }
    }
}
