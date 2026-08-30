package com.riskplatform.engine.adapter.dryrun;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.engine.application.DryRunService;
import com.riskplatform.engine.application.DryRunService.StartCommand;
import com.riskplatform.engine.domain.dryrun.DryRunJob;
import com.riskplatform.engine.domain.dryrun.DryRunSampleSource;
import com.riskplatform.engine.domain.dryrun.DryRunTargetType;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 试运行 REST 适配器（影子模式，R5.1/R5.3）。
 *
 * <p>端点：
 * <ul>
 *   <li>{@code POST /api/v1/dry-run}：发起试运行，立即返回 jobId 与 RUNNING 状态（异步执行）；</li>
 *   <li>{@code GET  /api/v1/dry-run/{id}}：查询试运行任务与报告（report_json）。</li>
 * </ul>
 *
 * <p>发起人取自 Spring Security 主体（经 BFF 透传 JWT）；未认证时记为 anonymous。
 * 任务不存在时经 {@code GlobalExceptionHandler} 映射为 404。
 */
@RestController
@RequestMapping("/api/v1/dry-run")
public class DryRunController {

    private final DryRunService dryRunService;

    public DryRunController(DryRunService dryRunService) {
        this.dryRunService = dryRunService;
    }

    /**
     * 发起试运行（R5.1）。
     *
     * @param req 发起请求
     * @return jobId + 状态 RUNNING
     */
    @PostMapping
    public StartView start(@RequestBody StartRequest req) {
        DryRunTargetType targetType = parseTargetType(req.targetType());
        DryRunSampleSource sampleSource = parseSampleSource(req.sampleSource());
        if (req.targetId() == null) {
            throw badRequest("目标ID不能为空: targetId");
        }
        StartCommand cmd = new StartCommand(
                targetType,
                req.targetId(),
                sampleSource,
                parseDateTime(req.dataFrom(), "dataFrom"),
                parseDateTime(req.dataTo(), "dataTo"),
                req.sampleLimit() == null ? 0 : req.sampleLimit(),
                currentUser());
        Long jobId = dryRunService.start(cmd);
        return new StartView(jobId, "RUNNING");
    }

    /**
     * 查询试运行报告（R5.3）。
     *
     * @param id 任务 id
     * @return 任务视图（状态 + 统计 + 报告 JSON）
     */
    @GetMapping("/{id}")
    public ReportView get(@PathVariable("id") long id) {
        DryRunJob job = dryRunService.query(id)
                .orElseThrow(() -> BizException.notFound("试运行任务不存在: " + id));
        return ReportView.from(job);
    }

    // —— 内部辅助 ——

    private static BizException badRequest(String message) {
        return new BizException(CommonErrorCode.INVALID_FIELD, message);
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "anonymous";
        }
        return auth.getName();
    }

    private static DryRunTargetType parseTargetType(String v) {
        if (v == null || v.isBlank()) {
            throw badRequest("目标类型不能为空: targetType");
        }
        try {
            return DryRunTargetType.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw badRequest("目标类型非法（应为 RULE/RULE_PACKAGE）: " + v);
        }
    }

    private static DryRunSampleSource parseSampleSource(String v) {
        if (v == null || v.isBlank()) {
            throw badRequest("样本来源不能为空: sampleSource");
        }
        try {
            return DryRunSampleSource.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw badRequest("样本来源非法（应为 ORDER/EVENT）: " + v);
        }
    }

    private static LocalDateTime parseDateTime(String v, String field) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw badRequest("时间格式非法（应为 ISO-8601，如 2024-01-01T00:00:00）: " + field);
        }
    }

    // —— 请求/响应 DTO ——

    /**
     * 发起试运行请求（R5.1）。
     *
     * @param targetType   目标类型 RULE/RULE_PACKAGE
     * @param targetId     目标 id
     * @param sampleSource 样本来源 ORDER/EVENT
     * @param dataFrom     样本起始时间（ISO-8601，可空）
     * @param dataTo       样本结束时间（ISO-8601，可空）
     * @param sampleLimit  样本数量上限（可空，&lt;=0 表示不限）
     */
    public record StartRequest(@NotNull String targetType,
                               @NotNull Long targetId,
                               @NotNull String sampleSource,
                               String dataFrom,
                               String dataTo,
                               Integer sampleLimit) {
    }

    /** 发起响应：任务 id 与初始状态。 */
    public record StartView(Long jobId, String status) {
    }

    /**
     * 试运行任务视图（R5.3）。report 字段为报告 JSON（分布/明细摘要），可由前端解析下钻。
     */
    public record ReportView(Long jobId,
                             String targetType,
                             Long targetId,
                             String sampleSource,
                             String status,
                             int totalCount,
                             int hitCount,
                             BigDecimal hitRate,
                             int errorCount,
                             String report) {

        static ReportView from(DryRunJob job) {
            return new ReportView(
                    job.getId(),
                    job.getTargetType().name(),
                    job.getTargetId(),
                    job.getSampleSource().name(),
                    job.getStatus().name(),
                    job.getTotalCount(),
                    job.getHitCount(),
                    job.getHitRate(),
                    job.getErrorCount(),
                    job.getReportJson());
        }
    }
}
