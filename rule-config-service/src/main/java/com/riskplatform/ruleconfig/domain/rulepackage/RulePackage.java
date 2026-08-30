package com.riskplatform.ruleconfig.domain.rulepackage;

import com.riskplatform.common.error.ValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 规则包聚合根（R1）。
 *
 * <p>规则包是规则的容器，具有触发模式（命中/评分），归属场景与机构，是平台阶段一的核心配置实体，
 * 对应表 {@code rule_package} 及关联表 {@code rule_package_scenario}/{@code rule_package_event}/
 * {@code rule_package_score_band}。
 *
 * <p>关键不变式：
 * <ul>
 *   <li><b>触发模式创建后不可变</b>（R1.1）：仅 {@link #create} 与 {@link #rehydrate} 设置 triggerMode，
 *       聚合不提供任何修改触发模式的方法。</li>
 *   <li>name 长度 1..128、code 长度 1..64 且仅含字母/数字/下划线（R1.5）。</li>
 *   <li><b>同一触发模式下名称全局唯一</b>（R1.4）：唯一性需查询其它规则包，故由应用层调用
 *       {@link #checkNameUnique(NameUniquenessChecker)} 校验钩子完成，领域层仅定义钩子契约。</li>
 *   <li><b>评分模式分值区间两两不重叠</b>（R1.6）：由 {@link ScoreBand#validateNonOverlapping(List)} 保证，
 *       可含负分区间。</li>
 *   <li>命中模式不得配置分值区间；评分模式启用预警单阈值时需提供比较符与阈值（R4.5）。</li>
 * </ul>
 *
 * <p>本类为纯领域对象，不依赖框架。校验失败抛出 {@link ValidationException}（聚合字段级错误）。
 */
public class RulePackage {

    public static final int NAME_MAX = 128;
    public static final int CODE_MAX = 64;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private Long id;
    private String code;
    private String name;
    /** 触发模式，创建后不可变（R1.1）。 */
    private TriggerMode triggerMode;
    private ComputeMode computeMode;
    private String riskTypeCode;
    private Long ownerOrgId;
    private Long applicableOrgId;
    private boolean includeSubOrg;
    private RulePackageStatus status;
    /** 是否启用生成预警单分值阈值（仅评分模式有意义）。 */
    private boolean warnScoreEnabled;
    private WarnScoreOp warnScoreOp;
    private BigDecimal warnScoreThreshold;
    private int version;

    /** 归属场景 id 集合（去重、保持插入顺序）。 */
    private final List<Long> scenarioIds = new ArrayList<>();
    /** 决策事件类型编码集合（去重、保持插入顺序，支持多个）。 */
    private final List<String> eventTypeCodes = new ArrayList<>();
    /** 评分模式分值区间列表（命中模式应为空）。 */
    private final List<ScoreBand> scoreBands = new ArrayList<>();

    private RulePackage() {
    }

    /**
     * 校验钩子：同一触发模式下规则包名称是否唯一（R1.4）。
     *
     * <p>由应用层注入实现（查询仓储），领域层在 {@link #checkNameUnique(NameUniquenessChecker)} 中调用。
     */
    @FunctionalInterface
    public interface NameUniquenessChecker {
        /**
         * @param triggerMode 触发模式
         * @param name        规则包名称
         * @param selfId      当前规则包 id（更新场景排除自身；创建场景传 null）
         * @return 若已存在同触发模式同名（排除自身）的规则包则返回 true
         */
        boolean existsByTriggerModeAndName(TriggerMode triggerMode, String name, Long selfId);
    }

    /**
     * 工厂方法：创建一个规则包（默认禁用状态，需显式启用），并执行输入校验。
     *
     * <p>触发模式一经创建即固定，后续不可变更（R1.1）。
     */
    public static RulePackage create(String code, String name, TriggerMode triggerMode, ComputeMode computeMode,
                                     String riskTypeCode, Long ownerOrgId, Long applicableOrgId, boolean includeSubOrg) {
        RulePackage p = new RulePackage();
        p.code = code;
        p.name = name;
        p.triggerMode = triggerMode;
        p.computeMode = computeMode == null ? ComputeMode.ONLINE : computeMode;
        p.riskTypeCode = riskTypeCode;
        p.ownerOrgId = ownerOrgId;
        p.applicableOrgId = applicableOrgId;
        p.includeSubOrg = includeSubOrg;
        p.status = RulePackageStatus.DISABLED;
        p.warnScoreEnabled = false;
        p.version = 1;
        p.validate();
        return p;
    }

    /** 从持久化重建（不重复校验，可重建出与创建时一致的触发模式）。 */
    public static RulePackage rehydrate(Long id, String code, String name, TriggerMode triggerMode,
                                        ComputeMode computeMode, String riskTypeCode, Long ownerOrgId,
                                        Long applicableOrgId, boolean includeSubOrg, RulePackageStatus status,
                                        boolean warnScoreEnabled, WarnScoreOp warnScoreOp,
                                        BigDecimal warnScoreThreshold, int version,
                                        List<Long> scenarioIds, List<String> eventTypeCodes,
                                        List<ScoreBand> scoreBands) {
        RulePackage p = new RulePackage();
        p.id = id;
        p.code = code;
        p.name = name;
        p.triggerMode = triggerMode;
        p.computeMode = computeMode;
        p.riskTypeCode = riskTypeCode;
        p.ownerOrgId = ownerOrgId;
        p.applicableOrgId = applicableOrgId;
        p.includeSubOrg = includeSubOrg;
        p.status = status;
        p.warnScoreEnabled = warnScoreEnabled;
        p.warnScoreOp = warnScoreOp;
        p.warnScoreThreshold = warnScoreThreshold;
        p.version = version;
        if (scenarioIds != null) {
            p.scenarioIds.addAll(new LinkedHashSet<>(scenarioIds));
        }
        if (eventTypeCodes != null) {
            p.eventTypeCodes.addAll(new LinkedHashSet<>(eventTypeCodes));
        }
        if (scoreBands != null) {
            p.scoreBands.addAll(scoreBands);
        }
        return p;
    }

    /** 校验基础字段不变式，违反时抛出聚合字段错误。 */
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
        if (triggerMode == null) {
            errors.field("triggerMode", "必填");
        }
        if (computeMode == null) {
            errors.field("computeMode", "必填");
        }
        // 命中模式不应配置分值区间
        if (triggerMode == TriggerMode.HIT && !scoreBands.isEmpty()) {
            errors.field("scoreBands", "命中模式不支持配置分值区间");
        }
        // 预警单阈值：启用时需提供比较符与阈值，且仅评分模式有意义
        if (warnScoreEnabled) {
            if (triggerMode != TriggerMode.SCORE) {
                errors.field("warnScoreEnabled", "仅评分模式可启用预警单分值阈值");
            }
            if (warnScoreOp == null) {
                errors.field("warnScoreOp", "启用预警单阈值时必填");
            }
            if (warnScoreThreshold == null) {
                errors.field("warnScoreThreshold", "启用预警单阈值时必填");
            }
        }
        errors.throwIfAny();
    }

    /**
     * 名称唯一性校验钩子（R1.4）：由应用层注入仓储查询实现。
     *
     * <p>存在同触发模式同名规则包时，抛出 name 字段级错误。
     */
    public void checkNameUnique(NameUniquenessChecker checker) {
        if (checker == null) {
            return;
        }
        if (checker.existsByTriggerModeAndName(triggerMode, name, id)) {
            ValidationException.builder()
                    .field("name", "同一触发模式下规则包名称已存在")
                    .throwIfAny();
        }
    }

    /** 更新基础信息（不改触发模式，R1.1），并重新校验。 */
    public void updateBasics(String name, ComputeMode computeMode, String riskTypeCode,
                             Long ownerOrgId, Long applicableOrgId, boolean includeSubOrg) {
        this.name = name;
        this.computeMode = computeMode == null ? this.computeMode : computeMode;
        this.riskTypeCode = riskTypeCode;
        this.ownerOrgId = ownerOrgId;
        this.applicableOrgId = applicableOrgId;
        this.includeSubOrg = includeSubOrg;
        validate();
    }

    /** 配置预警单分值阈值（仅评分模式，R4.5），并重新校验。 */
    public void configureWarnScore(boolean enabled, WarnScoreOp op, BigDecimal threshold) {
        this.warnScoreEnabled = enabled;
        this.warnScoreOp = enabled ? op : null;
        this.warnScoreThreshold = enabled ? threshold : null;
        validate();
    }

    /** 全量替换归属场景（去重，保持顺序）。 */
    public void replaceScenarios(List<Long> ids) {
        scenarioIds.clear();
        if (ids != null) {
            for (Long sid : ids) {
                if (sid != null && !scenarioIds.contains(sid)) {
                    scenarioIds.add(sid);
                }
            }
        }
    }

    /** 全量替换决策事件（去重，保持顺序，R1.5：支持多个）。 */
    public void replaceEvents(List<String> codes) {
        eventTypeCodes.clear();
        if (codes != null) {
            for (String c : codes) {
                if (c != null && !c.isBlank() && !eventTypeCodes.contains(c)) {
                    eventTypeCodes.add(c);
                }
            }
        }
    }

    /**
     * 全量替换分值区间（R1.6）：仅评分模式允许；校验两两不重叠（可含负分）。
     */
    public void replaceScoreBands(List<ScoreBand> bands) {
        if (triggerMode != TriggerMode.SCORE) {
            ValidationException.builder()
                    .field("scoreBands", "仅评分模式可配置分值区间")
                    .throwIfAny();
        }
        List<ScoreBand> incoming = bands == null ? Collections.emptyList() : bands;
        ScoreBand.validateNonOverlapping(incoming);
        scoreBands.clear();
        scoreBands.addAll(incoming);
    }

    /** 启用（R1.7）。 */
    public void enable() {
        this.status = RulePackageStatus.ENABLED;
    }

    /** 禁用（R1.7）。 */
    public void disable() {
        this.status = RulePackageStatus.DISABLED;
    }

    public boolean isEnabled() {
        return this.status == RulePackageStatus.ENABLED;
    }

    /** 版本递增（配置变更时调用）。 */
    public void bumpVersion() {
        this.version++;
    }

    public Long getId() {
        return id;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public TriggerMode getTriggerMode() {
        return triggerMode;
    }

    public ComputeMode getComputeMode() {
        return computeMode;
    }

    public String getRiskTypeCode() {
        return riskTypeCode;
    }

    public Long getOwnerOrgId() {
        return ownerOrgId;
    }

    public Long getApplicableOrgId() {
        return applicableOrgId;
    }

    public boolean isIncludeSubOrg() {
        return includeSubOrg;
    }

    public RulePackageStatus getStatus() {
        return status;
    }

    public boolean isWarnScoreEnabled() {
        return warnScoreEnabled;
    }

    public WarnScoreOp getWarnScoreOp() {
        return warnScoreOp;
    }

    public BigDecimal getWarnScoreThreshold() {
        return warnScoreThreshold;
    }

    public int getVersion() {
        return version;
    }

    /** 返回不可变的归属场景 id 列表。 */
    public List<Long> getScenarioIds() {
        return Collections.unmodifiableList(scenarioIds);
    }

    /** 返回不可变的决策事件编码列表。 */
    public List<String> getEventTypeCodes() {
        return Collections.unmodifiableList(eventTypeCodes);
    }

    /** 返回不可变的分值区间列表。 */
    public List<ScoreBand> getScoreBands() {
        return Collections.unmodifiableList(scoreBands);
    }
}
