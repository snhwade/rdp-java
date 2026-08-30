package com.riskplatform.ruleconfig.adapter.scenario;

import com.riskplatform.ruleconfig.application.scenario.ScenarioAppService;
import com.riskplatform.ruleconfig.domain.scenario.Scenario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * 场景 REST 适配器（R10.1/R10.4）。
 *
 * <p>端点：
 * <ul>
 *   <li>POST   /api/v1/scenarios 创建（R10.1）</li>
 *   <li>PUT    /api/v1/scenarios/{id} 更新名称与关联事件（R10.1）</li>
 *   <li>PUT    /api/v1/scenarios/{id}/status 启用/禁用</li>
 *   <li>GET    /api/v1/scenarios 列表</li>
 *   <li>GET    /api/v1/scenarios/{id} 详情（含关联事件）</li>
 *   <li>POST   /api/v1/scenarios/{id}/events 设置场景-事件关联（全量替换）</li>
 *   <li>GET    /api/v1/scenarios/{id}/events 查询场景关联事件（R10.4）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {

    private final ScenarioAppService appService;

    public ScenarioController(ScenarioAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public ScenarioView create(@Valid @RequestBody CreateScenarioRequest req) {
        return ScenarioView.from(appService.create(req.code(), req.name(), req.eventTypeCodes()));
    }

    @PutMapping("/{id}")
    public ScenarioView update(@PathVariable Long id, @Valid @RequestBody UpdateScenarioRequest req) {
        return ScenarioView.from(appService.update(id, req.name(), req.eventTypeCodes()));
    }

    @PutMapping("/{id}/status")
    public ScenarioView setStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        return ScenarioView.from(appService.setStatus(id, enabled));
    }

    @GetMapping
    public List<ScenarioView> list() {
        return appService.list().stream().map(ScenarioView::from).toList();
    }

    @GetMapping("/{id}")
    public ScenarioView get(@PathVariable Long id) {
        return ScenarioView.from(appService.get(id));
    }

    @PostMapping("/{id}/events")
    public ScenarioView setEvents(@PathVariable Long id, @RequestBody EventsRequest req) {
        return ScenarioView.from(appService.replaceEvents(id, req.eventTypeCodes()));
    }

    @GetMapping("/{id}/events")
    public List<String> listEvents(@PathVariable Long id) {
        return appService.listEvents(id);
    }

    /** 创建请求。基础非空校验由 Bean Validation 完成，长度/字符集校验在领域层。 */
    public record CreateScenarioRequest(@NotBlank String code, @NotBlank String name,
                                        List<String> eventTypeCodes) {
    }

    /** 更新请求（不可改 code）。 */
    public record UpdateScenarioRequest(@NotBlank String name, List<String> eventTypeCodes) {
    }

    /** 场景-事件关联请求。 */
    public record EventsRequest(List<String> eventTypeCodes) {
    }

    /** 视图对象。 */
    public record ScenarioView(Long id, String code, String name, String status,
                               List<String> eventTypeCodes) {
        static ScenarioView from(Scenario s) {
            return new ScenarioView(s.getId(), s.getCode(), s.getName(), s.getStatus().name(),
                    s.getEventTypeCodes());
        }
    }
}
