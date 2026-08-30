package com.riskplatform.ruleconfig.domain.ratingmodel;

import com.riskplatform.common.error.ValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 评级模型聚合根（risk-console-redesign，R10/R11/R12/R13）。
 *
 * <p>一个评级模型挂在某事件下，用于对评级主体（商户/对私客户）定级。模型由
 * 执行方式（实时/定时）、定级方式（评分定级/直接定级）、可视化等级区间集合
 * {@link GradeBand} 与评级子项/定级项集合 {@link RatingItem} 组成；模型整体随
 * 每次保存写入版本快照并维护当前版本号与上下线状态。
 *
 * <p>不变式（基础，加载/保存均校验 {@link #validate()}）：name/eventTypeCode 必填；
 * executionMode ∈ {REALTIME, SCHEDULED}、subject ∈ {MERCHANT, INDIVIDUAL}、
 * gradingMode ∈ {SCORE_BASED, DIRECT}（R10.3）。
 *
 * <p>等级区间深度校验（重叠/覆盖缺口）由 {@link #validateGradeBands()} 承载：在基础结构校验
 * （min&le;max、grade 必填）之上，按下界升序判定各区间互不重叠且连续覆盖模型分值范围，
 * 存在重叠或覆盖缺口则拒绝并报错（R11.3/R11.4）；等级数量不受限制（R11.2）。
 */
public class RatingModel {

    /** 执行方式：实时 / 定时（R10.3）。 */
    public enum ExecutionMode {
        REALTIME,
        SCHEDULED
    }

    /** 评级主体：商户（对公） / 对私客户（R10.3）。 */
    public enum Subject {
        MERCHANT,
        INDIVIDUAL
    }

    /** 定级方式：评分定级 / 直接定级 / 混合（同一模型可同时配置两类子项）。 */
    public enum GradingMode {
        SCORE_BASED,
        DIRECT,
        MIXED
    }

    /** 上下线状态：已上线。 */
    public static final String STATUS_ONLINE = "ONLINE";
    /** 上下线状态：已下线（默认）。 */
    public static final String STATUS_OFFLINE = "OFFLINE";

    private Long id;
    private String name;
    private String eventTypeCode;
    private ExecutionMode executionMode;
    private Subject subject;
    private GradingMode gradingMode;
    private List<GradeBand> gradeBands;
    private List<RatingItem> items;
    private String status;
    private int version;

    private RatingModel() {
    }

    /**
     * 仅以名称 + 所属事件 + 执行方式 + 评级主体 + 定级方式创建评级模型（R10.2）。
     *
     * <p>用于卡片墙「新建评级模型」入口：此时尚无等级区间与子项配置，初始化为空集合，
     * 由后续编辑保存补全。状态默认下线、版本号 1。
     */
    public static RatingModel create(String name, String eventTypeCode, ExecutionMode executionMode,
                                     Subject subject, GradingMode gradingMode) {
        return create(name, eventTypeCode, executionMode, subject, gradingMode, List.of(), List.of());
    }

    /** 以完整配置创建评级模型（含等级区间与子项），保存期由应用服务额外调用 {@link #validateGradeBands()}。 */
    public static RatingModel create(String name, String eventTypeCode, ExecutionMode executionMode,
                                     Subject subject, GradingMode gradingMode,
                                     List<GradeBand> gradeBands, List<RatingItem> items) {
        RatingModel m = new RatingModel();
        m.name = name;
        m.eventTypeCode = eventTypeCode;
        m.executionMode = executionMode;
        m.subject = subject;
        m.gradingMode = gradingMode;
        m.gradeBands = gradeBands == null ? new ArrayList<>() : new ArrayList<>(gradeBands);
        m.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
        m.status = STATUS_OFFLINE;
        m.version = 1;
        m.validate();
        return m;
    }

    /** 从持久化记录重建（仓储加载用），不执行保存期深度校验以兼容历史数据。 */
    public static RatingModel rehydrate(Long id, String name, String eventTypeCode,
                                        ExecutionMode executionMode, Subject subject, GradingMode gradingMode,
                                        List<GradeBand> gradeBands, List<RatingItem> items,
                                        String status, int version) {
        RatingModel m = new RatingModel();
        m.id = id;
        m.name = name;
        m.eventTypeCode = eventTypeCode;
        m.executionMode = executionMode;
        m.subject = subject;
        m.gradingMode = gradingMode;
        m.gradeBands = gradeBands == null ? new ArrayList<>() : new ArrayList<>(gradeBands);
        m.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
        m.status = status == null ? STATUS_OFFLINE : status;
        m.version = version;
        return m;
    }

    /**
     * 基础不变式校验（加载与保存均执行）：名称/事件必填，执行方式/评级主体/定级方式三枚举非空（R10.3）。
     *
     * <p>执行方式与评级主体的「取值合法性」由 Java 枚举在解析入参时天然保证——非法字符串无法构造
     * 出对应枚举，应在适配器/应用层解析阶段抛出字段级错误；此处仅断言三枚举不为空。
     */
    public void validate() {
        ValidationException.Builder errors = ValidationException.builder();
        if (name == null || name.isBlank()) {
            errors.field("name", "必填");
        }
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            errors.field("eventTypeCode", "必填");
        }
        if (executionMode == null) {
            errors.field("executionMode", "必填，取值须为 REALTIME/SCHEDULED 之一");
        }
        if (subject == null) {
            errors.field("subject", "必填，取值须为 MERCHANT/INDIVIDUAL 之一");
        }
        if (gradingMode == null) {
            errors.field("gradingMode", "必填，取值须为 SCORE_BASED/DIRECT/MIXED 之一");
        }
        errors.throwIfAny();
    }

    /**
     * 等级区间深度校验（R11.2/R11.3/R11.4）。
     *
     * <p>校验分两阶段：
     * <ol>
     *   <li><b>结构校验</b>：每个区间非空、grade 必填、上下界必填且 min&le;max（最低一致性）。</li>
     *   <li><b>重叠 / 覆盖缺口校验</b>：将区间按下界升序排列后，相邻区间须严格衔接——
     *       后一区间下界等于前一区间上界（{@code next.min == prev.max}）。若 {@code next.min < prev.max}
     *       判为区间重叠；若 {@code next.min > prev.max} 判为存在覆盖缺口（模型分值范围内有未被任何等级
     *       覆盖的分数）。二者均拒绝保存并报错（R11.4）。</li>
     * </ol>
     *
     * <p><b>边界约定（左闭右闭 [min, max]，与引擎侧 {@code GradeBand.contains()} 一致）</b>：
     * 区间上下界均为闭区间，相邻区间在共享边界点（{@code prev.max == next.min}）处都会命中，
     * 引擎按区间升序取首个命中者以保证归属确定性。因此衔接处共享单点是<b>允许的连续覆盖</b>而非重叠，
     * 配置侧与引擎侧映射对边界处理保持一致。
     *
     * <p>「模型分值范围」由各等级区间自身的最小下界到最大上界给出，连续覆盖即指排序后相邻区间之间
     * 无缺口。等级数量不受限制（R11.2）：空集合与单区间天然满足，无需衔接判定。
     *
     * <p>由应用服务在创建/保存（新建版本）时调用；仓储加载既有数据时不调用。
     */
    public void validateGradeBands() {
        ValidationException.Builder errors = ValidationException.builder();
        boolean structurallyValid = true;
        if (gradeBands != null) {
            for (int i = 0; i < gradeBands.size(); i++) {
                GradeBand b = gradeBands.get(i);
                if (b == null) {
                    errors.field("gradeBands[" + i + "]", "等级区间不可为空");
                    structurallyValid = false;
                    continue;
                }
                if (b.grade() == null || b.grade().isBlank()) {
                    errors.field("gradeBands[" + i + "].grade", "等级必填");
                }
                if (b.minScore() == null || b.maxScore() == null) {
                    errors.field("gradeBands[" + i + "]", "区间上下界必填");
                    structurallyValid = false;
                } else if (b.minScore().compareTo(b.maxScore()) > 0) {
                    errors.field("gradeBands[" + i + "]",
                            "区间下界不可大于上界: [" + b.minScore() + "," + b.maxScore() + "]");
                    structurallyValid = false;
                }
            }
        }
        // 仅当结构校验通过（上下界均存在且 min<=max）时才做重叠/覆盖缺口判定，避免空界比较 NPE。
        if (structurallyValid && gradeBands != null && gradeBands.size() > 1) {
            List<GradeBand> sorted = new ArrayList<>(gradeBands);
            sorted.sort((a, b) -> {
                int c = a.minScore().compareTo(b.minScore());
                return c != 0 ? c : a.maxScore().compareTo(b.maxScore());
            });
            for (int i = 1; i < sorted.size(); i++) {
                GradeBand prev = sorted.get(i - 1);
                GradeBand curr = sorted.get(i);
                int cmp = curr.minScore().compareTo(prev.maxScore());
                if (cmp < 0) {
                    errors.field("gradeBands.overlap",
                            "等级区间存在重叠: [" + prev.minScore() + "," + prev.maxScore()
                                    + "] 与 [" + curr.minScore() + "," + curr.maxScore() + "]");
                } else if (cmp > 0) {
                    errors.field("gradeBands.gap",
                            "等级区间存在覆盖缺口，分值范围未被连续覆盖: (" + prev.maxScore()
                                    + "," + curr.minScore() + ") 无等级覆盖");
                }
            }
        }
        errors.throwIfAny();
    }

    /**
     * 编辑评级模型配置（保存→新建版本场景）：可更新名称、定级方式、等级区间与子项。
     * 执行方式与评级主体作为模型固有属性，创建后不在此处变更。
     */
    public void update(String name, GradingMode gradingMode, List<GradeBand> gradeBands, List<RatingItem> items) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (gradingMode != null) {
            this.gradingMode = gradingMode;
        }
        if (gradeBands != null) {
            this.gradeBands = new ArrayList<>(gradeBands);
        }
        if (items != null) {
            this.items = new ArrayList<>(items);
        }
        validate();
    }

    /** 版本号自增（保存→新建版本时调用）。 */
    public void bumpVersion() {
        this.version = this.version + 1;
    }

    /** 置为已上线（R10.7）。 */
    public void online() {
        this.status = STATUS_ONLINE;
    }

    /** 置为已下线（R10.7）。 */
    public void offline() {
        this.status = STATUS_OFFLINE;
    }

    /** 是否已上线。 */
    public boolean isOnline() {
        return STATUS_ONLINE.equals(status);
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public Subject getSubject() {
        return subject;
    }

    public GradingMode getGradingMode() {
        return gradingMode;
    }

    public List<GradeBand> getGradeBands() {
        return gradeBands;
    }

    public List<RatingItem> getItems() {
        return items;
    }

    public String getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    /**
     * 可视化等级区间（R11.1）。
     *
     * @param minScore 区间下界（含）
     * @param maxScore 区间上界（含）
     * @param grade    区间对应等级（如 一级/二级）
     * @param orderNo  等级序（用于等级高低比较，越大或越小由定级方式约定，引擎侧消费）
     */
    public record GradeBand(BigDecimal minScore, BigDecimal maxScore, String grade, int orderNo) {
    }

    /**
     * 评级子项（评分定级，R12.1）/ 定级项（直接定级，R13.1）合一承载。
     *
     * <p>评分定级使用 category/subItem/condition/score/subItemCap/importance；
     * 直接定级使用 condition/grade。空字段按对应定级方式忽略。
     *
     * @param category   评级类别（评分定级）
     * @param subItem    评级子项名称（评分定级）
     * @param condition  命中条件表达式
     * @param score      子项计入分值（评分定级）
     * @param subItemCap 子项分值上限（评分定级）
     * @param importance 重要度（评分定级）
     * @param grade      命中等级（直接定级）
     */
    public record RatingItem(String category, String subItem, String condition,
                             BigDecimal score, BigDecimal subItemCap, String importance, String grade) {
    }
}
