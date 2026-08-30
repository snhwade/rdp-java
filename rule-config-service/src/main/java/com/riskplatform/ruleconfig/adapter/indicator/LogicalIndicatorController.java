package com.riskplatform.ruleconfig.adapter.indicator;

import com.riskplatform.ruleconfig.application.indicator.LogicalIndicatorAppService;
import com.riskplatform.ruleconfig.domain.indicator.CombineMode;
import com.riskplatform.ruleconfig.domain.indicator.LogicalIndicator;
import com.riskplatform.ruleconfig.domain.indicator.LogicalIndicatorMember;
import com.riskplatform.ruleconfig.domain.indicator.SliceGranularity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/logical-indicators")
public class LogicalIndicatorController {

    private final LogicalIndicatorAppService appService;

    public LogicalIndicatorController(LogicalIndicatorAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public LogicalView create(@Valid @RequestBody SaveLogicalRequest req) {
        return LogicalView.from(appService.create(
                req.groupId(), req.refName(), req.name(), req.description(),
                CombineMode.valueOf(req.combineMode()),
                req.combineExpression(), req.dimensions(), req.windowDays(),
                SliceGranularity.valueOf(req.sliceGranularity()),
                req.defaultValueStrategy(), toMembers(req.members())));
    }

    @GetMapping
    public List<LogicalView> list(
            @RequestParam(name = "groupId", required = false) Long groupId,
            @RequestParam(name = "ungrouped", required = false) Boolean ungrouped,
            @RequestParam(name = "status", required = false) String status) {
        return appService.list(groupId, ungrouped, status).stream().map(LogicalView::from).toList();
    }

    @PutMapping("/{id}")
    public LogicalView update(@PathVariable Long id, @Valid @RequestBody SaveLogicalRequest req) {
        return LogicalView.from(appService.update(
                id, req.groupId(), req.name(), req.description(),
                CombineMode.valueOf(req.combineMode()),
                req.combineExpression(), req.dimensions(), req.windowDays(),
                SliceGranularity.valueOf(req.sliceGranularity()),
                req.defaultValueStrategy(), toMembers(req.members())));
    }

    @PutMapping("/{id}/online")
    public LogicalView online(@PathVariable Long id) {
        return LogicalView.from(appService.online(id));
    }

    @PutMapping("/{id}/offline")
    public LogicalView offline(@PathVariable Long id) {
        return LogicalView.from(appService.offline(id));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        appService.delete(id);
    }

    @GetMapping("/references")
    public List<String> references(@RequestParam("refName") String refName) {
        return appService.findReferences(refName);
    }

    private static List<LogicalIndicatorMember> toMembers(List<MemberRequest> members) {
        if (members == null) {
            return List.of();
        }
        java.util.ArrayList<LogicalIndicatorMember> result = new java.util.ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            MemberRequest m = members.get(i);
            result.add(new LogicalIndicatorMember(m.memberRefName(), m.eventTypeCode(), i));
        }
        return result;
    }

    public record MemberRequest(@NotBlank String memberRefName, String eventTypeCode) {
    }

    public record SaveLogicalRequest(
            Long groupId,
            @NotBlank String refName,
            String name,
            String description,
            @NotBlank String combineMode,
            String combineExpression,
            @NotEmpty List<String> dimensions,
            int windowDays,
            @NotBlank String sliceGranularity,
            String defaultValueStrategy,
            @NotEmpty List<MemberRequest> members) {
    }

    public record LogicalView(
            Long id,
            Long groupId,
            String refName,
            String name,
            String description,
            String combineMode,
            String combineExpression,
            List<String> dimensions,
            int windowDays,
            String sliceGranularity,
            String defaultValueStrategy,
            String status,
            List<MemberView> members,
            String indicatorKind) {

        static LogicalView from(LogicalIndicator li) {
            return new LogicalView(
                    li.getId(), li.getGroupId(), li.getRefName(), li.getName(), li.getDescription(),
                    li.getCombineMode().name(), li.getCombineExpression(),
                    li.getDimensions(), li.getWindowDays(), li.getSliceGranularity().name(),
                    li.getDefaultValueStrategy(), li.getStatus(),
                    li.getMembers().stream()
                            .map(m -> new MemberView(m.memberRefName(), m.eventTypeCode(), m.sortOrder()))
                            .toList(),
                    "LOGICAL");
        }

        public record MemberView(String memberRefName, String eventTypeCode, int sortOrder) {
        }
    }
}
