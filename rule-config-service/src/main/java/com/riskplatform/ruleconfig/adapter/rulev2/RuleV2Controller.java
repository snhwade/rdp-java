package com.riskplatform.ruleconfig.adapter.rulev2;

import com.riskplatform.common.error.ValidationException;
import com.riskplatform.ruleconfig.application.rulev2.RuleV2AppService;
import com.riskplatform.ruleconfig.application.rulev2.RuleV2AppService.CompilePreviewResult;
import com.riskplatform.ruleconfig.application.rulev2.RuleV2AppService.CreateCommand;
import com.riskplatform.ruleconfig.application.rulev2.RuleV2AppService.UpdateCommand;
import com.riskplatform.ruleconfig.domain.rulev2.DynamicScoreBand;
import com.riskplatform.ruleconfig.domain.rulev2.RuleKind;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2Status;
import com.riskplatform.ruleconfig.domain.rulev2.condition.ConditionNode;
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
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构化规则 REST 适配器（R2.5/R2.6/R2.7）。
 *
 * <p>端点：
 * <ul>
 *   <li>POST   /api/v1/rules-v2 创建（R2.5：保存时编译条件树落库；R2.7：未声明字段/指标字段级错误）</li>
 *   <li>PUT    /api/v1/rules-v2/{id} 更新（重新编译，递增 expr_version 与规则版本）</li>
 *   <li>GET    /api/v1/rules-v2 列表</li>
 *   <li>GET    /api/v1/rules-v2/{id} 详情（含条件树/编译表达式/动态分）</li>
 *   <li>PUT    /api/v1/rules-v2/{id}/status 三态切换（ONLINE/TRIAL_RUN/OFFLINE，返回更新后状态，R7.2）</li>
 *   <li>POST   /api/v1/rules-v2/compile-preview 编译预览（不落库，返回表达式与校验结果）</li>
 * </ul>
 *
 * <p>条件树以 design.md 的 condition_json 结构由 Jackson 直接反序列化为 {@link ConditionNode}。
 * 基础非空校验由 Bean Validation 完成；条件结构/运算符适配/编译/未声明校验在领域与应用层。
 */
@RestController
@RequestMapping("/api/v1/rules-v2")
public class RuleV2Controller {

    private final RuleV2AppService appService;

    public RuleV2Controller(RuleV2AppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public RuleV2View create(@Valid @RequestBody CreateRuleV2Request req) {
        CreateCommand cmd = new CreateCommand(
                req.code(), req.name(), req.rulePackageId(), RuleKind.valueOf(req.ruleKind()),
                req.eventTypeCode(), req.riskLevelCode(), req.riskTypeCode(), req.baseScore(),
                req.condition(), intOr(req.priority(), 0), bool(req.shortCircuited()),
                req.applicableOrgId(), bool(req.includeSubOrg()), req.remark(),
                toDynamicScores(req.dynamicScores()));
        return RuleV2View.detail(appService.create(cmd));
    }

    @PutMapping("/{id}")
    public RuleV2View update(@PathVariable Long id, @Valid @RequestBody UpdateRuleV2Request req) {
        UpdateCommand cmd = new UpdateCommand(
                req.name(), req.eventTypeCode(), req.riskLevelCode(), req.riskTypeCode(),
                req.baseScore(), req.condition(), intOr(req.priority(), 0), bool(req.shortCircuited()),
                req.applicableOrgId(), bool(req.includeSubOrg()), req.remark(),
                toDynamicScores(req.dynamicScores()));
        return RuleV2View.detail(appService.update(id, cmd));
    }

    @GetMapping
    public List<RuleV2View> list() {
        return appService.list().stream().map(RuleV2View::summary).toList();
    }

    @GetMapping("/{id}")
    public RuleV2View get(@PathVariable Long id) {
        return RuleV2View.detail(appService.get(id));
    }

    @PutMapping("/{id}/status")
    public RuleStatusView changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeRuleStatusRequest req) {
        RuleV2 updated = appService.changeStatus(id, toRuleStatus(req.status()));
        return new RuleStatusView(updated.getId(), updated.getStatus().name());
    }

    /** 解析三态（兼容历史 ENABLED/DISABLED 字面量）。 */
    private static RuleV2Status toRuleStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            ValidationException.builder()
                    .field("status", "目标状态必填（ONLINE / TRIAL_RUN / OFFLINE）")
                    .throwIfAny();
        }
        return switch (raw.trim().toUpperCase()) {
            case "ONLINE", "ENABLED" -> RuleV2Status.ONLINE;
            case "TRIAL_RUN" -> RuleV2Status.TRIAL_RUN;
            case "OFFLINE", "DISABLED" -> RuleV2Status.OFFLINE;
            default -> {
                ValidationException.builder()
                        .field("status", "状态只能为 ONLINE / TRIAL_RUN / OFFLINE")
                        .throwIfAny();
                yield RuleV2Status.OFFLINE;
            }
        };
    }

    @PostMapping("/compile-preview")
    public CompilePreviewResult compilePreview(@Valid @RequestBody CompilePreviewRequest req) {
        return appService.compilePreview(req.condition());
    }

    // —— 内部辅助 ——

    private static boolean bool(Boolean v) {
        return v != null && v;
    }

    private static int intOr(Integer v, int def) {
        return v == null ? def : v;
    }

    private static List<DynamicScoreBand> toDynamicScores(List<DynamicScoreDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<DynamicScoreBand> bands = new ArrayList<>(dtos.size());
        int i = 0;
        for (DynamicScoreDto d : dtos) {
            int orderNo = d.orderNo() == null ? i : d.orderNo();
            bands.add(DynamicScoreBand.of(d.indicatorRefName(), d.lower(), d.upper(),
                    d.lowerInclusive() == null || d.lowerInclusive(),
                    d.upperInclusive() != null && d.upperInclusive(),
                    d.score(), orderNo));
            i++;
        }
        return bands;
    }

    // —— 请求/响应 DTO ——

    /** 创建请求。规则类型创建后不可变（R2）。 */
    public record CreateRuleV2Request(
            @NotBlank String code,
            @NotBlank String name,
            Long rulePackageId,
            @NotNull String ruleKind,
            String eventTypeCode,
            String riskLevelCode,
            String riskTypeCode,
            BigDecimal baseScore,
            @NotNull ConditionNode condition,
            Integer priority,
            Boolean shortCircuited,
            Long applicableOrgId,
            Boolean includeSubOrg,
            String remark,
            List<DynamicScoreDto> dynamicScores) {
    }

    /** 更新请求（不含 code 与 ruleKind）。 */
    public record UpdateRuleV2Request(
            @NotBlank String name,
            String eventTypeCode,
            String riskLevelCode,
            String riskTypeCode,
            BigDecimal baseScore,
            @NotNull ConditionNode condition,
            Integer priority,
            Boolean shortCircuited,
            Long applicableOrgId,
            Boolean includeSubOrg,
            String remark,
            List<DynamicScoreDto> dynamicScores) {
    }

    /** 编译预览请求（仅条件树，不落库）。 */
    public record CompilePreviewRequest(@NotNull ConditionNode condition) {
    }

    /** 规则三态切换请求（R7.2）：status ∈ {ONLINE, TRIAL_RUN, OFFLINE}（兼容 ENABLED/DISABLED）。 */
    public record ChangeRuleStatusRequest(
            @NotNull @Pattern(regexp = "ONLINE|TRIAL_RUN|OFFLINE|ENABLED|DISABLED",
                    message = "状态只能为 ONLINE / TRIAL_RUN / OFFLINE") String status) {
    }

    /** 规则三态切换响应（R7.2）：返回更新后的状态。 */
    public record RuleStatusView(Long id, String status) {
    }

    /** 动态分区间数据传输对象（lowerInclusive 默认 true、upperInclusive 默认 false，左闭右开）。 */
    public record DynamicScoreDto(String indicatorRefName, BigDecimal lower, BigDecimal upper,
                                  Boolean lowerInclusive, Boolean upperInclusive,
                                  BigDecimal score, Integer orderNo) {
        static DynamicScoreDto from(DynamicScoreBand b) {
            return new DynamicScoreDto(b.indicatorRefName(), b.lower(), b.upper(),
                    b.lowerInclusive(), b.upperInclusive(), b.score(), b.orderNo());
        }
    }

    /**
     * 结构化规则视图对象。列表用 {@link #summary} 输出概要；详情用 {@link #detail} 含条件树/编译表达式/动态分。
     */
    public record RuleV2View(
            Long id, String code, String name, Long rulePackageId, String ruleKind,
            String eventTypeCode, String riskLevelCode, String riskTypeCode, BigDecimal baseScore,
            ConditionNode condition, String compiledExpr, int exprVersion, int priority,
            boolean shortCircuited, Long applicableOrgId, boolean includeSubOrg, String remark,
            int version, String status, List<DynamicScoreDto> dynamicScores) {

        static RuleV2View summary(RuleV2 r) {
            return new RuleV2View(r.getId(), r.getCode(), r.getName(), r.getRulePackageId(),
                    r.getRuleKind().name(), r.getEventTypeCode(), r.getRiskLevelCode(), r.getRiskTypeCode(),
                    r.getBaseScore(), null, null, r.getExprVersion(), r.getPriority(),
                    r.isShortCircuited(), r.getApplicableOrgId(), r.isIncludeSubOrg(), r.getRemark(),
                    r.getVersion(), r.getStatus().name(), null);
        }

        static RuleV2View detail(RuleV2 r) {
            return new RuleV2View(r.getId(), r.getCode(), r.getName(), r.getRulePackageId(),
                    r.getRuleKind().name(), r.getEventTypeCode(), r.getRiskLevelCode(), r.getRiskTypeCode(),
                    r.getBaseScore(), r.getCondition(), r.getCompiledExpr(), r.getExprVersion(), r.getPriority(),
                    r.isShortCircuited(), r.getApplicableOrgId(), r.isIncludeSubOrg(), r.getRemark(),
                    r.getVersion(), r.getStatus().name(),
                    r.getDynamicScores().stream().map(DynamicScoreDto::from).toList());
        }
    }
}
