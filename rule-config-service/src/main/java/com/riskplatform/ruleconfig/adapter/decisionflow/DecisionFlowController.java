package com.riskplatform.ruleconfig.adapter.decisionflow;

import com.riskplatform.ruleconfig.application.decisionflow.DecisionFlowAppService;
import com.riskplatform.ruleconfig.application.decisionflow.DecisionFlowVersionAppService;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlow;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowVersion;
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

/**
 * 决策流 REST 适配器（S4）。
 *
 * <ul>
 *   <li>POST   /api/v1/decision-flows      新建</li>
 *   <li>GET    /api/v1/decision-flows?eventTypeCode= 列表</li>
 *   <li>GET    /api/v1/decision-flows/{id} 详情</li>
 *   <li>PUT    /api/v1/decision-flows/{id} 更新</li>
 *   <li>DELETE /api/v1/decision-flows/{id} 删除</li>
 *   <li>GET    /api/v1/decision-flows/{id}/versions 版本列表（R6.5）</li>
 *   <li>GET    /api/v1/decision-flows/{id}/versions/compare?from=&to= 两版本对比（R6.5）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/decision-flows")
public class DecisionFlowController {

    private final DecisionFlowAppService appService;
    private final DecisionFlowVersionAppService versionAppService;

    public DecisionFlowController(DecisionFlowAppService appService,
                                  DecisionFlowVersionAppService versionAppService) {
        this.appService = appService;
        this.versionAppService = versionAppService;
    }

    @PostMapping
    public DecisionFlow create(@RequestBody CreateRequest req) {
        // R8.2：卡片墙「添加决策流」仅提供名称 + 所属事件时，初始化最小画布（START→END）；
        // 提供了完整画布内容（nodes/edges/startNodeId）时按完整创建处理。
        boolean hasCanvas = req.nodes() != null && !req.nodes().isEmpty()
                && req.edges() != null && !req.edges().isEmpty()
                && req.startNodeId() != null && !req.startNodeId().isBlank();
        if (!hasCanvas) {
            return appService.create(req.name(), req.eventTypeCode(), req.remark());
        }
        return appService.create(req.name(), req.eventTypeCode(), req.nodes(), req.edges(), req.startNodeId(),
                req.scenarioIds(), req.eventCodes(), req.applicableOrgId(),
                req.includeSubOrg() != null && req.includeSubOrg(), req.remark());
    }

    @GetMapping
    public List<DecisionFlow> list(@RequestParam(name = "eventTypeCode", required = false) String eventTypeCode) {
        List<DecisionFlow> flows = appService.list(eventTypeCode);
        java.util.Set<Long> onlineIds = versionAppService.findOnlineFlowIds(
                flows.stream().map(DecisionFlow::getId).toList());
        for (DecisionFlow flow : flows) {
            flow.setCardStatus(onlineIds.contains(flow.getId())
                    ? DecisionFlowVersion.STATUS_ONLINE
                    : DecisionFlowVersion.STATUS_OFFLINE);
        }
        return flows;
    }

    @GetMapping("/{id}")
    public DecisionFlow get(@PathVariable("id") Long id) {
        return appService.get(id);
    }

    @PutMapping("/{id}")
    public DecisionFlow update(@PathVariable("id") Long id, @RequestBody UpdateRequest req) {
        return appService.update(id, req.name(), req.nodes(), req.edges(), req.startNodeId(),
                req.status() == null ? "ENABLED" : req.status(),
                req.scenarioIds(), req.eventCodes(), req.applicableOrgId(),
                req.includeSubOrg() != null && req.includeSubOrg(), req.remark());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        appService.delete(id);
    }

    /** 版本列表（R6.5）：按版本号降序返回历史版本元数据。 */
    @GetMapping("/{id}/versions")
    public List<DecisionFlowVersionAppService.VersionSummary> versions(@PathVariable("id") Long id) {
        return versionAppService.listVersions(id);
    }

    /**
     * 版本上线（R8.6）：将指定版本置为已上线，并将该决策流原先处于已上线状态的版本置为已下线，
     * 保证任一时刻至多一个上线版本。
     *
     * @param id      决策流 ID
     * @param version 待上线的版本号
     */
    @PostMapping("/{id}/versions/{version}:online")
    public List<DecisionFlowVersionAppService.VersionSummary> onlineVersion(
            @PathVariable("id") Long id,
            @PathVariable("version") int version) {
        versionAppService.onlineVersion(id, version);
        return versionAppService.listVersions(id);
    }

    /** 回退到上一启用版本（R1）。 */
    @PostMapping("/{id}:rollback-last-online")
    public List<DecisionFlowVersionAppService.VersionSummary> rollbackLastOnline(
            @PathVariable("id") Long id) {
        versionAppService.rollbackToPreviousOnline(id);
        return versionAppService.listVersions(id);
    }

    /**
     * 决策流下线（R8.7）：将该决策流当前处于已上线状态的版本置为已下线。
     *
     * @param id 决策流 ID
     */
    @PostMapping("/{id}:offline")
    public List<DecisionFlowVersionAppService.VersionSummary> offline(@PathVariable("id") Long id) {
        versionAppService.offlineFlow(id);
        return versionAppService.listVersions(id);
    }

    /**
     * 两版本差异对比（R6.5）：返回两份完整快照 + 字段级差异标注。
     *
     * @param id   决策流 ID
     * @param from 起始版本号
     * @param to   目标版本号
     */
    @GetMapping("/{id}/versions/compare")
    public DecisionFlowVersionAppService.CompareResult compareVersions(
            @PathVariable("id") Long id,
            @RequestParam("from") int from,
            @RequestParam("to") int to) {
        return versionAppService.compare(id, from, to);
    }

    public record CreateRequest(
            @NotBlank String name,
            @NotBlank String eventTypeCode,
            List<DecisionFlow.Node> nodes,
            List<DecisionFlow.Edge> edges,
            String startNodeId,
            List<Long> scenarioIds,
            List<String> eventCodes,
            Long applicableOrgId,
            Boolean includeSubOrg,
            String remark) {
    }

    public record UpdateRequest(
            @NotBlank String name,
            @NotEmpty List<DecisionFlow.Node> nodes,
            @NotEmpty List<DecisionFlow.Edge> edges,
            @NotBlank String startNodeId,
            String status,
            List<Long> scenarioIds,
            List<String> eventCodes,
            Long applicableOrgId,
            Boolean includeSubOrg,
            String remark) {
    }
}
