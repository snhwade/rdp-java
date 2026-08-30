package com.riskplatform.ruleconfig.adapter.indicator;

import com.riskplatform.ruleconfig.application.indicator.IndicatorDefinitionAppService;
import com.riskplatform.ruleconfig.application.indicator.IndicatorDefinitionSnapshotAppService;
import com.riskplatform.ruleconfig.application.indicator.IndicatorRuntimeStatsAppService;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinition;
import com.riskplatform.ruleconfig.domain.indicator.SliceGranularity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 指标定义 REST 适配器（R7）。
 */
@RestController
@RequestMapping("/api/v1/indicator-definitions")
public class IndicatorDefinitionController {

    private final IndicatorDefinitionAppService appService;
    private final IndicatorDefinitionSnapshotAppService snapshotAppService;
    private final IndicatorRuntimeStatsAppService runtimeStatsAppService;

    public IndicatorDefinitionController(IndicatorDefinitionAppService appService,
                                         IndicatorDefinitionSnapshotAppService snapshotAppService,
                                         IndicatorRuntimeStatsAppService runtimeStatsAppService) {
        this.appService = appService;
        this.snapshotAppService = snapshotAppService;
        this.runtimeStatsAppService = runtimeStatsAppService;
    }

    @PostMapping
    public IndicatorView create(@Valid @RequestBody CreateIndicatorRequest req) {
        IndicatorDefinition def = appService.create(
                req.groupId(), req.refName(), req.name(), req.description(), req.eventTypeCodes(),
                req.dimensions(), req.windowDays(),
                SliceGranularity.valueOf(req.sliceGranularity()), req.accScript(), req.defaultValueStrategy(),
                req.templateType(), req.templateConfig());
        return IndicatorView.from(def);
    }

    @GetMapping
    public List<IndicatorView> list(
            @RequestParam(name = "groupId", required = false) Long groupId,
            @RequestParam(name = "ungrouped", required = false) Boolean ungrouped,
            @RequestParam(name = "eventTypeCode", required = false) String eventTypeCode,
            @RequestParam(name = "status", required = false) String status) {
        return appService.list(groupId, ungrouped, eventTypeCode, status).stream().map(IndicatorView::from).toList();
    }

    @PutMapping("/{id}")
    public IndicatorView update(@PathVariable("id") Long id,
                                @Valid @RequestBody UpdateIndicatorRequest req) {
        IndicatorDefinition def = appService.update(
                id, req.groupId(), req.name(), req.description(), req.eventTypeCodes(),
                req.dimensions(), req.windowDays(),
                SliceGranularity.valueOf(req.sliceGranularity()), req.accScript(), req.defaultValueStrategy(),
                req.templateType(), req.templateConfig());
        return IndicatorView.from(def);
    }

    @PutMapping("/{id}/online")
    public IndicatorView online(@PathVariable("id") Long id) {
        return IndicatorView.from(appService.online(id));
    }

    @PutMapping("/{id}/offline")
    public IndicatorView offline(@PathVariable("id") Long id) {
        return IndicatorView.from(appService.offline(id));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        appService.delete(id);
    }

    @GetMapping("/references")
    public List<String> references(@RequestParam("refName") String refName) {
        return appService.findReferences(refName);
    }

    @GetMapping("/{id}/definition-snapshots")
    public List<SnapshotView> listSnapshots(@PathVariable("id") Long id) {
        return snapshotAppService.listSnapshots(id).stream()
                .map(s -> new SnapshotView(s.version(), s.createdBy(), s.createdAt()))
                .toList();
    }

    @PostMapping("/{id}/rollback-last-definition")
    public IndicatorView rollbackLastDefinition(@PathVariable("id") Long id) {
        return IndicatorView.from(snapshotAppService.rollbackToPreviousDefinition(id));
    }

    @GetMapping("/runtime-stats")
    public List<RuntimeStatsView> runtimeStats(
            @RequestParam(name = "groupId", required = false) Long groupId,
            @RequestParam(name = "refName", required = false) String refName) {
        if (refName != null && !refName.isBlank()) {
            return List.of(toRuntimeStatsView(runtimeStatsAppService.getByRefName(refName)));
        }
        if (groupId != null) {
            return runtimeStatsAppService.listByGroupId(groupId).stream()
                    .map(this::toRuntimeStatsView).toList();
        }
        return List.of();
    }

    private RuntimeStatsView toRuntimeStatsView(IndicatorRuntimeStatsAppService.RuntimeStatsView v) {
        return new RuntimeStatsView(
                v.refName(), v.status(), v.lastAccumulateAt(), v.readMissCount(), v.indicatorDefinitionId());
    }

    public record SnapshotView(int version, String createdBy, java.time.Instant createdAt) {
    }

    public record RuntimeStatsView(
            String refName,
            String status,
            java.time.Instant lastAccumulateAt,
            long readMissCount,
            Long indicatorDefinitionId) {
    }

    public record CreateIndicatorRequest(
            Long groupId,
            @NotBlank String refName,
            String name,
            String description,
            @NotEmpty List<String> eventTypeCodes,
            @NotEmpty List<String> dimensions,
            int windowDays,
            @NotBlank String sliceGranularity,
            @NotBlank String accScript,
            String defaultValueStrategy,
            String templateType,
            Map<String, Object> templateConfig) {
    }

    public record UpdateIndicatorRequest(
            Long groupId,
            String name,
            String description,
            @NotEmpty List<String> eventTypeCodes,
            @NotEmpty List<String> dimensions,
            int windowDays,
            @NotBlank String sliceGranularity,
            @NotBlank String accScript,
            String defaultValueStrategy,
            String templateType,
            Map<String, Object> templateConfig) {
    }

    public record IndicatorView(
            Long id,
            Long groupId,
            String refName,
            String name,
            String description,
            List<String> eventTypeCodes,
            List<String> dimensions,
            int windowDays,
            String sliceGranularity,
            String accScript,
            String defaultValueStrategy,
            String status,
            String templateType,
            Map<String, Object> templateConfig) {

        static IndicatorView from(IndicatorDefinition d) {
            return new IndicatorView(
                    d.getId(), d.getGroupId(), d.getRefName(), d.getName(), d.getDescription(),
                    d.getEventTypeCodes(), d.getDimensions(),
                    d.getWindowDays(), d.getSliceGranularity().name(), d.getAccScript(),
                    d.getDefaultValueStrategy(), d.getStatus(),
                    d.getTemplateType(), d.getTemplateConfig());
        }
    }
}
