package com.riskplatform.ruleconfig.adapter.eventtype;

import com.riskplatform.ruleconfig.application.eventtype.EventTypeAppService;
import com.riskplatform.ruleconfig.application.scenario.ScenarioAppService;
import com.riskplatform.ruleconfig.domain.eventtype.EventEngineStatusQuery;
import com.riskplatform.ruleconfig.domain.eventtype.EventKind;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;
import com.riskplatform.ruleconfig.domain.eventtype.EventType;
import com.riskplatform.ruleconfig.domain.scenario.Scenario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 事件（参数管理）REST 适配器（risk-console-redesign R2）。
 *
 * <p>命名中性化：路径与标识不含任何产品厂商专有名词。
 *
 * <p>端点：
 * <ul>
 *   <li>GET    /api/v1/scenarios/tree         场景→事件树（R2.1）</li>
 *   <li>GET    /api/v1/events?scenarioId=     某场景下事件列表（R2.1）</li>
 *   <li>POST   /api/v1/events                 创建事件（R2.2–2.6）</li>
 *   <li>PUT    /api/v1/events/{id}            编辑事件（R2.7）</li>
 *   <li>DELETE /api/v1/events/{id}            删除事件（R2.8/2.9）</li>
 *   <li>POST   /api/v1/events/import          批量导入（逐条校验，R2.10）</li>
 *   <li>GET    /api/v1/events/{id}/engine-status 引擎可执行状态（R2.11）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class EventController {

    private final EventTypeAppService appService;
    private final ScenarioAppService scenarioAppService;

    public EventController(EventTypeAppService appService, ScenarioAppService scenarioAppService) {
        this.appService = appService;
        this.scenarioAppService = scenarioAppService;
    }

    /** 场景→事件树（R2.1）：左侧场景为父节点，下属事件为子节点。 */
    @GetMapping("/scenarios/tree")
    public List<ScenarioTreeNode> scenarioTree() {
        List<EventType> allEvents = appService.list();
        List<ScenarioTreeNode> tree = new ArrayList<>();
        for (Scenario scenario : scenarioAppService.list()) {
            List<EventView> children = allEvents.stream()
                    .filter(e -> scenario.getId().equals(e.getScenarioId()))
                    .map(EventView::from)
                    .toList();
            tree.add(new ScenarioTreeNode(scenario.getId(), scenario.getCode(),
                    scenario.getName(), children));
        }
        return tree;
    }

    /** 某场景下事件列表（R2.1）。未提供 scenarioId 时返回全部事件。 */
    @GetMapping("/events")
    public List<EventView> listEvents(@RequestParam(required = false) Long scenarioId) {
        List<EventType> events = scenarioId == null
                ? appService.list()
                : appService.listByScenario(scenarioId);
        return events.stream().map(EventView::from).toList();
    }

    /** 创建事件（R2.2–2.6）。 */
    @PostMapping("/events")
    public EventView createEvent(@Valid @RequestBody EventRequest req) {
        EventType created = appService.create(req.code(), req.name(), req.scenarioId(),
                parsePurposes(req.purposes()), parseKind(req.eventKind()));
        return EventView.from(created);
    }

    /** 编辑事件（R2.7）。 */
    @PutMapping("/events/{id}")
    public EventView updateEvent(@PathVariable Long id, @Valid @RequestBody UpdateEventRequest req) {
        EventType updated = appService.edit(id, req.name(), req.scenarioId(),
                parsePurposes(req.purposes()), parseKind(req.eventKind()));
        return EventView.from(updated);
    }

    /** 删除事件（R2.8/2.9）。 */
    @DeleteMapping("/events/{id}")
    public void deleteEvent(@PathVariable Long id) {
        appService.delete(id);
    }

    /** 批量导入事件（R2.10）：逐条校验，返回成功数与每条失败原因。 */
    @PostMapping("/events/import")
    public ImportResultView importEvents(@RequestBody List<EventRequest> requests) {
        List<EventTypeAppService.ImportItem> items = new ArrayList<>();
        for (EventRequest r : requests == null ? List.<EventRequest>of() : requests) {
            items.add(new EventTypeAppService.ImportItem(r.code(), r.name(), r.scenarioId(),
                    parsePurposes(r.purposes()), parseKind(r.eventKind())));
        }
        EventTypeAppService.ImportResult result = appService.importEvents(items);
        List<EventView> succeeded = result.succeeded().stream().map(EventView::from).toList();
        List<ImportFailureView> failures = result.failures().stream()
                .map(f -> new ImportFailureView(f.index(), f.code(), f.reason()))
                .toList();
        return new ImportResultView(succeeded.size(), failures.size(), succeeded, failures);
    }

    /** 查询事件在引擎中的可执行状态（R2.11）。 */
    @GetMapping("/events/{id}/engine-status")
    public EngineStatusView engineStatus(@PathVariable Long id) {
        EventEngineStatusQuery.Status status = appService.engineStatus(id);
        return new EngineStatusView(id, status.name());
    }

    // —— 解析辅助 ——

    private Set<EventPurpose> parsePurposes(List<String> raw) {
        Set<EventPurpose> result = new LinkedHashSet<>();
        if (raw != null) {
            for (String p : raw) {
                if (p != null && !p.isBlank()) {
                    result.add(EventPurpose.valueOf(p.trim().toUpperCase()));
                }
            }
        }
        return result;
    }

    private EventKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return EventKind.valueOf(raw.trim().toUpperCase());
    }

    // —— 请求/视图对象 ——

    /** 创建事件请求。基础非空由 Bean Validation 完成，扩展必填项/用途/分型校验在领域层。 */
    public record EventRequest(@NotBlank String code, @NotBlank String name, Long scenarioId,
                               List<String> purposes, String eventKind) {
    }

    /** 编辑事件请求（不可改 code）。 */
    public record UpdateEventRequest(@NotBlank String name, Long scenarioId,
                                     List<String> purposes, String eventKind) {
    }

    /** 事件视图对象。 */
    public record EventView(Long id, String code, String name, String status, Long scenarioId,
                            List<String> purposes, String eventKind) {
        static EventView from(EventType e) {
            return new EventView(e.getId(), e.getCode(), e.getName(), e.getStatus().name(),
                    e.getScenarioId(),
                    e.getPurposes().stream().map(Enum::name).toList(),
                    e.getEventKind() == null ? null : e.getEventKind().name());
        }
    }

    /** 场景树节点（场景 + 下属事件）。 */
    public record ScenarioTreeNode(Long id, String code, String name, List<EventView> events) {
    }

    /** 单条导入失败视图。 */
    public record ImportFailureView(int index, String code, String reason) {
    }

    /** 批量导入结果视图。 */
    public record ImportResultView(int successCount, int failureCount,
                                   List<EventView> succeeded, List<ImportFailureView> failures) {
    }

    /** 引擎状态视图。 */
    public record EngineStatusView(Long eventId, String engineStatus) {
    }
}
