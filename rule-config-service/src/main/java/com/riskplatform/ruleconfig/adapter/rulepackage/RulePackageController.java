package com.riskplatform.ruleconfig.adapter.rulepackage;

import com.riskplatform.ruleconfig.application.rulepackage.RulePackageAppService;
import com.riskplatform.ruleconfig.application.rulepackage.RulePackageAppService.BatchCommand;
import com.riskplatform.ruleconfig.application.rulepackage.RulePackageAppService.BatchItemResult;
import com.riskplatform.ruleconfig.application.rulepackage.RulePackageAppService.BatchOperation;
import com.riskplatform.ruleconfig.application.rulepackage.RulePackageAppService.CreateCommand;
import com.riskplatform.ruleconfig.application.rulepackage.RulePackageAppService.RulePackageWithCounts;
import com.riskplatform.ruleconfig.application.rulepackage.RulePackageAppService.UpdateCommand;
import com.riskplatform.ruleconfig.domain.rulepackage.ComputeMode;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackage;
import com.riskplatform.ruleconfig.domain.rulepackage.ScoreBand;
import com.riskplatform.ruleconfig.domain.rulepackage.TriggerMode;
import com.riskplatform.ruleconfig.domain.rulepackage.WarnScoreOp;
import com.riskplatform.ruleconfig.domain.rulev2.RuleListItem;
import com.riskplatform.ruleconfig.domain.rulev2.RuleStatusCounts;
import com.riskplatform.ruleconfig.domain.rulev2.RuleStatusCounts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则包 REST 适配器（R1.1/R1.5/R1.6/R1.7）。
 *
 * <p>端点：
 * <ul>
 *   <li>POST   /api/v1/rule-packages 创建（R1.1/R1.4/R1.5/R1.6）</li>
 *   <li>PUT    /api/v1/rule-packages/{id} 更新基础信息（不改触发模式）</li>
 *   <li>GET    /api/v1/rule-packages 列表</li>
 *   <li>GET    /api/v1/rule-packages/{id} 详情（含场景/事件/分值区间/关联规则）</li>
 *   <li>PUT    /api/v1/rule-packages/{id}/status 启用/禁用（R1.7，配置变更广播 5s 生效）</li>
 *   <li>POST   /api/v1/rule-packages/{id}/score-bands 全量替换分值区间（R1.6）</li>
 *   <li>POST   /api/v1/rule-packages/{id}/rules 关联规则（R1.7，支持多包）</li>
 * </ul>
 *
 * <p>基础非空校验由 Bean Validation 完成，长度/字符集/区间不重叠等领域校验在聚合内。
 */
@RestController
@RequestMapping("/api/v1/rule-packages")
public class RulePackageController {

    private final RulePackageAppService appService;

    public RulePackageController(RulePackageAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public RulePackageView create(@Valid @RequestBody CreateRulePackageRequest req) {
        CreateCommand cmd = new CreateCommand(
                req.code(), req.name(), TriggerMode.valueOf(req.triggerMode()),
                parseComputeMode(req.computeMode()), req.riskTypeCode(), req.ownerOrgId(),
                req.applicableOrgId(), bool(req.includeSubOrg()), req.scenarioIds(),
                req.eventTypeCodes(), toScoreBands(req.scoreBands()),
                bool(req.warnScoreEnabled()), parseWarnScoreOp(req.warnScoreOp()), req.warnScoreThreshold());
        return detailView(appService.create(cmd));
    }

    @PutMapping("/{id}")
    public RulePackageView update(@PathVariable Long id, @Valid @RequestBody UpdateRulePackageRequest req) {
        UpdateCommand cmd = new UpdateCommand(
                req.name(), parseComputeMode(req.computeMode()), req.riskTypeCode(), req.ownerOrgId(),
                req.applicableOrgId(), bool(req.includeSubOrg()), req.scenarioIds(), req.eventTypeCodes(),
                bool(req.warnScoreEnabled()), parseWarnScoreOp(req.warnScoreOp()), req.warnScoreThreshold());
        return detailView(appService.update(id, cmd));
    }

    @GetMapping
    public List<RulePackageCardView> list(@RequestParam(required = false) String eventCode) {
        return appService.listByEventCodeWithCounts(eventCode).stream()
                .map(RulePackageCardView::from)
                .toList();
    }

    @GetMapping("/{id}")
    public RulePackageView get(@PathVariable Long id) {
        return detailView(appService.get(id));
    }

    @GetMapping("/{id}/rules")
    public List<RuleListItemView> listRules(@PathVariable Long id) {
        return appService.listRules(id).stream().map(RuleListItemView::from).toList();
    }

    @PostMapping("/{id}/rules:batch")
    public List<BatchItemResultView> batchOperate(@PathVariable Long id,
                                                  @Valid @RequestBody BatchRulesRequest req) {
        BatchCommand cmd = new BatchCommand(
                BatchOperation.valueOf(req.operation()), req.ruleIds(), req.targetRulePackageId(),
                req.copyCodePrefix(), req.applicableOrgId(), bool(req.includeSubOrg()));
        return appService.batchOperate(id, cmd).stream().map(BatchItemResultView::from).toList();
    }

    @PutMapping("/{id}/status")
    public RulePackageView setStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        return detailView(appService.setStatus(id, enabled));
    }

    /** 启用快照列表（P2）。 */
    @GetMapping("/{id}/enabled-snapshots")
    public List<EnabledSnapshotView> listEnabledSnapshots(@PathVariable Long id) {
        return appService.listEnabledSnapshots(id).stream()
                .map(s -> new EnabledSnapshotView(s.version(), s.createdBy(),
                        s.createdAt() == null ? null : s.createdAt().toString()))
                .toList();
    }

    /** 回退到上一启用快照（P2）。 */
    @PostMapping("/{id}/rollback-last-enabled")
    public RulePackageView rollbackLastEnabled(@PathVariable Long id) {
        return detailView(appService.rollbackToPreviousEnabled(id));
    }

    @PostMapping("/{id}/score-bands")
    public RulePackageView replaceScoreBands(@PathVariable Long id, @RequestBody ScoreBandsRequest req) {
        return detailView(appService.replaceScoreBands(id, toScoreBands(req.scoreBands())));
    }

    @PostMapping("/{id}/rules")
    public RulePackageView associateRule(@PathVariable Long id, @Valid @RequestBody AssociateRuleRequest req) {
        appService.associateRule(id, req.ruleV2Id(), req.priority() == null ? 0 : req.priority());
        return detailView(appService.get(id));
    }

    // —— 内部辅助 ——

    private RulePackageView detailView(RulePackage pkg) {
        return RulePackageView.detail(pkg, appService.listRuleIds(pkg.getId()));
    }

    private static boolean bool(Boolean v) {
        return v != null && v;
    }

    private static ComputeMode parseComputeMode(String v) {
        return (v == null || v.isBlank()) ? null : ComputeMode.valueOf(v);
    }

    private static WarnScoreOp parseWarnScoreOp(String v) {
        return (v == null || v.isBlank()) ? null : WarnScoreOp.valueOf(v);
    }

    private static List<ScoreBand> toScoreBands(List<ScoreBandDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<ScoreBand> bands = new ArrayList<>(dtos.size());
        int i = 0;
        for (ScoreBandDto d : dtos) {
            int orderNo = d.orderNo() == null ? i : d.orderNo();
            bands.add(ScoreBand.of(d.lower(), d.upper(),
                    d.lowerInclusive() == null || d.lowerInclusive(),
                    d.upperInclusive() != null && d.upperInclusive(),
                    d.riskLevelCode(), orderNo));
            i++;
        }
        return bands;
    }

    // —— 请求/响应 DTO ——

    /** 创建请求。触发模式必填且创建后不可变（R1.1）。 */
    public record CreateRulePackageRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull String triggerMode,
            String computeMode,
            String riskTypeCode,
            Long ownerOrgId,
            Long applicableOrgId,
            Boolean includeSubOrg,
            List<Long> scenarioIds,
            List<String> eventTypeCodes,
            List<ScoreBandDto> scoreBands,
            Boolean warnScoreEnabled,
            String warnScoreOp,
            BigDecimal warnScoreThreshold) {
    }

    /** 更新请求（不含触发模式与 code）。 */
    public record UpdateRulePackageRequest(
            @NotBlank String name,
            String computeMode,
            String riskTypeCode,
            Long ownerOrgId,
            Long applicableOrgId,
            Boolean includeSubOrg,
            List<Long> scenarioIds,
            List<String> eventTypeCodes,
            Boolean warnScoreEnabled,
            String warnScoreOp,
            BigDecimal warnScoreThreshold) {
    }

    /** 分值区间请求。 */
    public record ScoreBandsRequest(List<ScoreBandDto> scoreBands) {
    }

    /** 关联规则请求。 */
    public record AssociateRuleRequest(@NotNull Long ruleV2Id, Integer priority) {
    }

    /**
     * 规则包批量操作请求（R6.5）。
     *
     * @param operation           操作类型 DELETE/COPY/MOVE/ONLINE/TRIAL_RUN/OFFLINE/EDIT_ORG/DOWNLOAD
     * @param ruleIds             选中规则 id 列表（必填非空）
     * @param targetRulePackageId 目标规则包 id（移动/复制用，可空）
     * @param copyCodePrefix      复制新规则编码前缀（复制用，可空）
     * @param applicableOrgId     适用机构 id（编辑机构用，可空）
     * @param includeSubOrg       是否含下级机构（编辑机构用）
     */
    public record BatchRulesRequest(
            @NotNull @Pattern(regexp = "DELETE|COPY|MOVE|ONLINE|TRIAL_RUN|OFFLINE|EDIT_ORG|DOWNLOAD",
                    message = "不支持的批量操作类型") String operation,
            @NotNull List<Long> ruleIds,
            Long targetRulePackageId,
            String copyCodePrefix,
            Long applicableOrgId,
            Boolean includeSubOrg) {
    }

    /**
     * 规则三态计数视图（R6.6）。
     */
    public record TristateCountsView(long online, long trialRun, long offline) {
        static TristateCountsView from(RuleStatusCounts c) {
            return new TristateCountsView(c.online(), c.trialRun(), c.offline());
        }
    }

    /**
     * 规则包卡片视图（R6.1）：卡片墙展示名称、归属、事件路径、分类与底部三态计数。
     */
    public record RulePackageCardView(
            Long id, String code, String name, String triggerMode, String riskTypeCode,
            Long ownerOrgId, Long applicableOrgId, String status,
            List<String> eventTypeCodes, TristateCountsView counts) {

        static RulePackageCardView from(RulePackageWithCounts wc) {
            RulePackage p = wc.rulePackage();
            return new RulePackageCardView(p.getId(), p.getCode(), p.getName(), p.getTriggerMode().name(),
                    p.getRiskTypeCode(), p.getOwnerOrgId(), p.getApplicableOrgId(), p.getStatus().name(),
                    p.getEventTypeCodes(), TristateCountsView.from(wc.counts()));
        }
    }

    /**
     * 规则列表项视图（R6.4）：规则编码、名称、状态、决策事件、风险等级、风险分值。
     */
    public record RuleListItemView(Long id, String code, String name, String status,
                                   String decisionEventCode, String riskLevelCode, BigDecimal riskScore,
                                   String remark) {
        static RuleListItemView from(RuleListItem i) {
            return new RuleListItemView(i.id(), i.code(), i.name(), normalizeRuleStatus(i.status()),
                    i.decisionEventCode(), i.riskLevelCode(), i.riskScore(), i.remark());
        }

        /** 历史 ENABLED/DISABLED 映射为三态，便于前端下拉展示。 */
        private static String normalizeRuleStatus(String status) {
            if (status == null) {
                return RuleStatusCounts.OFFLINE;
            }
            return switch (status) {
                case "ENABLED" -> RuleStatusCounts.ONLINE;
                case "DISABLED" -> RuleStatusCounts.OFFLINE;
                default -> status;
            };
        }
    }

    /** 启用快照摘要视图（P2）。 */
    public record EnabledSnapshotView(int version, String createdBy, String createdAt) {
    }

    /**
     * 批量操作逐条结果视图（R6.5）。
     */
    public record BatchItemResultView(Long ruleId, boolean success, String message,
                                      Long refRuleId, Object payload) {
        static BatchItemResultView from(BatchItemResult r) {
            return new BatchItemResultView(r.ruleId(), r.success(), r.message(), r.refRuleId(), r.payload());
        }
    }

    /** 分值区间数据传输对象（lowerInclusive 默认 true、upperInclusive 默认 false，左闭右开）。 */
    public record ScoreBandDto(BigDecimal lower, BigDecimal upper, Boolean lowerInclusive,
                               Boolean upperInclusive, String riskLevelCode, Integer orderNo) {
        static ScoreBandDto from(ScoreBand b) {
            return new ScoreBandDto(b.getLower(), b.getUpper(), b.isLowerInclusive(),
                    b.isUpperInclusive(), b.getRiskLevelCode(), b.getOrderNo());
        }
    }

    /**
     * 规则包视图对象。列表用 {@link #summary} 输出概要；详情用 {@link #detail} 含场景/事件/区间/规则。
     */
    public record RulePackageView(
            Long id, String code, String name, String triggerMode, String computeMode,
            String riskTypeCode, Long ownerOrgId, Long applicableOrgId, boolean includeSubOrg,
            String status, boolean warnScoreEnabled, String warnScoreOp, BigDecimal warnScoreThreshold,
            int version, List<Long> scenarioIds, List<String> eventTypeCodes,
            List<ScoreBandDto> scoreBands, List<Long> ruleIds) {

        static RulePackageView summary(RulePackage p) {
            return new RulePackageView(p.getId(), p.getCode(), p.getName(), p.getTriggerMode().name(),
                    p.getComputeMode().name(), p.getRiskTypeCode(), p.getOwnerOrgId(), p.getApplicableOrgId(),
                    p.isIncludeSubOrg(), p.getStatus().name(), p.isWarnScoreEnabled(),
                    p.getWarnScoreOp() == null ? null : p.getWarnScoreOp().name(), p.getWarnScoreThreshold(),
                    p.getVersion(), null, null, null, null);
        }

        static RulePackageView detail(RulePackage p, List<Long> ruleIds) {
            return new RulePackageView(p.getId(), p.getCode(), p.getName(), p.getTriggerMode().name(),
                    p.getComputeMode().name(), p.getRiskTypeCode(), p.getOwnerOrgId(), p.getApplicableOrgId(),
                    p.isIncludeSubOrg(), p.getStatus().name(), p.isWarnScoreEnabled(),
                    p.getWarnScoreOp() == null ? null : p.getWarnScoreOp().name(), p.getWarnScoreThreshold(),
                    p.getVersion(), p.getScenarioIds(), p.getEventTypeCodes(),
                    p.getScoreBands().stream().map(ScoreBandDto::from).toList(), ruleIds);
        }
    }
}
