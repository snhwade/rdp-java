package com.riskplatform.ruleconfig.adapter.eventtype;

import com.riskplatform.ruleconfig.application.eventtype.EventTypeAppService;
import com.riskplatform.ruleconfig.domain.eventtype.EventType;
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
 * 事件类型 REST 适配器（R1）。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/v1/event-types 创建（R1.1/R1.2/R1.3）</li>
 *   <li>PUT  /api/v1/event-types/{id}/status 启用/禁用（R1.4）</li>
 *   <li>GET  /api/v1/event-types 列表（R1.6）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/event-types")
public class EventTypeController {

    private final EventTypeAppService appService;

    public EventTypeController(EventTypeAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public EventTypeView create(@Valid @RequestBody CreateEventTypeRequest req) {
        return EventTypeView.from(appService.create(req.code(), req.name()));
    }

    @PutMapping("/{id}/status")
    public EventTypeView setStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        return EventTypeView.from(appService.setStatus(id, enabled));
    }

    @GetMapping
    public List<EventTypeView> list() {
        return appService.list().stream().map(EventTypeView::from).toList();
    }

    /** 创建请求。基础非空校验由 Bean Validation 完成，长度/字符集校验在领域层。 */
    public record CreateEventTypeRequest(@NotBlank String code, @NotBlank String name) {
    }

    /** 视图对象。 */
    public record EventTypeView(Long id, String code, String name, String status) {
        static EventTypeView from(EventType e) {
            return new EventTypeView(e.getId(), e.getCode(), e.getName(), e.getStatus().name());
        }
    }
}
