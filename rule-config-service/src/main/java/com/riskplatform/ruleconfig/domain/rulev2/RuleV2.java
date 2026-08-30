package com.riskplatform.ruleconfig.domain.rulev2;

import com.riskplatform.common.error.ValidationException;
import com.riskplatform.ruleconfig.domain.rulev2.condition.ConditionNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 结构化规则聚合根（R2/R4）。
 *
 * <p>对应表 {@code rule_v2} 及子表 {@code rule_dynamic_score}。规则条件以结构化条件树
 * {@link ConditionNode} 表达（R2），保存时由应用层用 {@code ConditionCompiler} 编译为
 * Aviator 表达式缓存到 {@code compiledExpr} 并配 {@code exprVersion} 版本号（R2.5/R14.2）。
 *
 * <p>关键不变式：
 * <ul>
 *   <li>code 长度 1..64 且仅含字母/数字/下划线；name 长度 1..128（与 schema 一致）。</li>
 *   <li>命中规则（{@link RuleKind#HIT}）不应配置基础分与动态分；评分规则（{@link RuleKind#SCORE}）
 *       基础分可为负、动态分区间两两不重叠（R4.1/R4.2/R4.6）。</li>
 *   <li>条件树本身的结构与运算符适配校验由 {@link ConditionNode#validate()} 完成（R2.2/R2.4）；
 *       编译产物（compiledExpr/exprVersion）由应用层注入，本聚合不直接依赖编译器。</li>
 * </ul>
 *
 * <p>编译缓存版本 {@code exprVersion}：每次成功重新编译时由应用层调用 {@link #applyCompiled}
 * 写入新表达式并递增；引擎按该版本命中缓存避免重复编译（R14.2）。
 *
 * <p>本类为纯领域对象，不依赖框架。校验失败抛出 {@link ValidationException}。
 */
public class RuleV2 {

    public static final int NAME_MAX = 128;
    public static final int CODE_MAX = 64;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private String code;
    private String name;
    private Long rulePackageId;
    private RuleKind ruleKind;
    private String eventTypeCode;
    private String riskLevelCode;
    private String riskTypeCode;
    /** 基础分（评分规则用，可为负；命中规则为空）。 */
    private BigDecimal baseScore;
    /** 结构化条件树（R2）。 */
    private ConditionNode condition;
    /** 条件树编译后的 Aviator 表达式（由应用层注入，R2.5）。 */
    private String compiledExpr;
    /** 编译缓存版本键（每次重编译递增，R14.2）。 */
    private int exprVersion;
    private int priority;
    private boolean shortCircuited;
    private Long applicableOrgId;
    private boolean includeSubOrg;
    private String remark;
    private int version;
    private RuleV2Status status;

    /** 评分规则动态分区间（命中规则应为空）。 */
    private final List<DynamicScoreBand> dynamicScores = new ArrayList<>();

    private RuleV2() {
    }

    /**
     * 工厂方法：创建结构化规则（默认下线状态，需显式上线），并执行输入校验。
     *
     * <p>条件树编译由应用层在保存前调用 {@link #applyCompiled} 注入；创建时仅保存条件树本体。
     */
    public static RuleV2 create(String code, String name, Long rulePackageId, RuleKind ruleKind,
                                String eventTypeCode, String riskLevelCode, String riskTypeCode,
                                BigDecimal baseScore, ConditionNode condition, int priority,
                                boolean shortCircuited, Long applicableOrgId, boolean includeSubOrg,
                                String remark) {
        RuleV2 r = new RuleV2();
        r.code = code;
        r.name = name;
        r.rulePackageId = rulePackageId;
        r.ruleKind = ruleKind;
        r.eventTypeCode = eventTypeCode;
        r.riskLevelCode = riskLevelCode;
        r.riskTypeCode = riskTypeCode;
        r.baseScore = baseScore;
        r.condition = condition;
        r.priority = priority;
        r.shortCircuited = shortCircuited;
        r.applicableOrgId = applicableOrgId;
        r.includeSubOrg = includeSubOrg;
        r.remark = remark;
        r.status = RuleV2Status.OFFLINE;
        r.version = 1;
        r.exprVersion = 0;
        r.validate();
        return r;
    }

    /** 从持久化重建（不重复校验）。 */
    public static RuleV2 rehydrate(Long id, String code, String name, Long rulePackageId, RuleKind ruleKind,
                                   String eventTypeCode, String riskLevelCode, String riskTypeCode,
                                   BigDecimal baseScore, ConditionNode condition, String compiledExpr,
                                   int exprVersion, int priority, boolean shortCircuited, Long applicableOrgId,
                                   boolean includeSubOrg, String remark, int version, RuleV2Status status,
                                   List<DynamicScoreBand> dynamicScores) {
        RuleV2 r = new RuleV2();
        r.id = id;
        r.code = code;
        r.name = name;
        r.rulePackageId = rulePackageId;
        r.ruleKind = ruleKind;
        r.eventTypeCode = eventTypeCode;
        r.riskLevelCode = riskLevelCode;
        r.riskTypeCode = riskTypeCode;
        r.baseScore = baseScore;
        r.condition = condition;
        r.compiledExpr = compiledExpr;
        r.exprVersion = exprVersion;
        r.priority = priority;
        r.shortCircuited = shortCircuited;
        r.applicableOrgId = applicableOrgId;
        r.includeSubOrg = includeSubOrg;
        r.remark = remark;
        r.version = version;
        r.status = status;
        if (dynamicScores != null) {
            r.dynamicScores.addAll(dynamicScores);
        }
        return r;
    }

    /** 校验基础字段不变式与评分配置约束，违反时抛出聚合字段错误。 */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (name == null || name.isEmpty()) {
            errors.field("name", "必填");
        } else if (name.length() > NAME_MAX) {
            errors.field("name", "长度不能超过 " + NAME_MAX + " 个字符");
        }
        if (code == null || code.isEmpty()) {
            errors.field("code", "必填");
        } else if (code.length() > CODE_MAX) {
            errors.field("code", "长度不能超过 " + CODE_MAX + " 个字符");
        } else if (!CODE_PATTERN.matcher(code).matches()) {
            errors.field("code", "只能包含字母、数字与下划线");
        }
        if (ruleKind == null) {
            errors.field("ruleKind", "必填");
        }
        if (condition == null) {
            errors.field("condition", "结构化条件树必填");
        }
        // 命中规则不应配置评分项
        if (ruleKind == RuleKind.HIT) {
            if (baseScore != null) {
                errors.field("baseScore", "命中规则不支持配置基础分");
            }
            if (!dynamicScores.isEmpty()) {
                errors.field("dynamicScores", "命中规则不支持配置动态分");
            }
        }
        errors.throwIfAny();
        // 条件树结构 / 运算符适配校验（R2.2/R2.4）
        if (condition != null) {
            condition.validate();
        }
        // 评分规则动态分区间不重叠校验（R4.6）
        if (ruleKind == RuleKind.SCORE) {
            DynamicScoreBand.validateNonOverlapping(dynamicScores);
        }
    }

    /**
     * 更新可变属性（不改 code/ruleKind），并重新校验。
     *
     * <p>触发模式类不可变项（ruleKind/code）不在更新范围内，保持双轨稳定。
     */
    public void update(String name, String eventTypeCode, String riskLevelCode, String riskTypeCode,
                       BigDecimal baseScore, ConditionNode condition, int priority, boolean shortCircuited,
                       Long applicableOrgId, boolean includeSubOrg, String remark) {
        this.name = name;
        this.eventTypeCode = eventTypeCode;
        this.riskLevelCode = riskLevelCode;
        this.riskTypeCode = riskTypeCode;
        this.baseScore = baseScore;
        this.condition = condition;
        this.priority = priority;
        this.shortCircuited = shortCircuited;
        this.applicableOrgId = applicableOrgId;
        this.includeSubOrg = includeSubOrg;
        this.remark = remark;
        validate();
    }

    /**
     * 全量替换动态分区间（R4.2/R4.6）：仅评分规则允许；校验区间两两不重叠。
     */
    public void replaceDynamicScores(List<DynamicScoreBand> bands) {
        if (ruleKind != RuleKind.SCORE) {
            ValidationException.builder()
                    .field("dynamicScores", "仅评分规则可配置动态分")
                    .throwIfAny();
        }
        List<DynamicScoreBand> incoming = bands == null ? Collections.emptyList() : bands;
        DynamicScoreBand.validateNonOverlapping(incoming);
        dynamicScores.clear();
        dynamicScores.addAll(incoming);
    }

    /**
     * 应用编译产物（R2.5/R14.2）：注入编译后的 Aviator 表达式并递增编译缓存版本。
     *
     * <p>由应用层在保存前用 {@code ConditionCompiler} 编译条件树后调用。
     *
     * @param compiledExpr 编译后的 Aviator 表达式
     */
    public void applyCompiled(String compiledExpr) {
        this.compiledExpr = compiledExpr;
        this.exprVersion++;
    }

    /** 切换为上线（R7.1/R7.2）：参与最终决策聚合。返回更新后的状态。 */
    public RuleV2Status online() {
        this.status = RuleV2Status.ONLINE;
        return this.status;
    }

    /** 切换为试运行（R7.1/R7.2）：被执行并返回结果，但不参与最终决策聚合。返回更新后的状态。 */
    public RuleV2Status trialRun() {
        this.status = RuleV2Status.TRIAL_RUN;
        return this.status;
    }

    /** 切换为下线（R7.1/R7.2）：不被执行。返回更新后的状态。 */
    public RuleV2Status offline() {
        this.status = RuleV2Status.OFFLINE;
        return this.status;
    }

    /** 是否上线（参与最终决策聚合）。 */
    public boolean isOnline() {
        return this.status == RuleV2Status.ONLINE;
    }

    /** 是否试运行（被执行但不参与最终决策聚合）。 */
    public boolean isTrialRun() {
        return this.status == RuleV2Status.TRIAL_RUN;
    }

    /** 规则版本递增（配置变更时调用，R2.5）。 */
    public void bumpVersion() {
        this.version++;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Long getRulePackageId() {
        return rulePackageId;
    }

    public RuleKind getRuleKind() {
        return ruleKind;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public String getRiskLevelCode() {
        return riskLevelCode;
    }

    public String getRiskTypeCode() {
        return riskTypeCode;
    }

    public BigDecimal getBaseScore() {
        return baseScore;
    }

    public ConditionNode getCondition() {
        return condition;
    }

    public String getCompiledExpr() {
        return compiledExpr;
    }

    public int getExprVersion() {
        return exprVersion;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isShortCircuited() {
        return shortCircuited;
    }

    public Long getApplicableOrgId() {
        return applicableOrgId;
    }

    public boolean isIncludeSubOrg() {
        return includeSubOrg;
    }

    public String getRemark() {
        return remark;
    }

    public int getVersion() {
        return version;
    }

    public RuleV2Status getStatus() {
        return status;
    }

    /** 返回不可变的动态分区间列表。 */
    public List<DynamicScoreBand> getDynamicScores() {
        return Collections.unmodifiableList(dynamicScores);
    }
}
