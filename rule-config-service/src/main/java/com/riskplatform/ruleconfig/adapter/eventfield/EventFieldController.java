package com.riskplatform.ruleconfig.adapter.eventfield;

import com.riskplatform.common.error.ValidationException;
import com.riskplatform.ruleconfig.application.eventfield.EventFieldAppService;
import com.riskplatform.ruleconfig.application.eventfield.EventFieldAppService.EventFieldView;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 事件字段 REST 适配器（risk-console-redesign R4）。
 *
 * <p>端点（挂在事件下，事件以 code 标识）：
 * <ul>
 *   <li>GET    /api/v1/events/{eventCode}/fields 列表（联接字段库 code/名称/类型，R4.1）</li>
 *   <li>POST   /api/v1/events/{eventCode}/fields 从字段库添加字段（R4.2/R4.3/R4.4）</li>
 *   <li>PUT    /api/v1/events/{eventCode}/fields/{eventFieldId}/derived 标记/取消衍生（R4.5）</li>
 *   <li>DELETE /api/v1/events/{eventCode}/fields/{eventFieldId} 移除（R4.6/R4.7）</li>
 * </ul>
 *
 * <p>字段级校验（用途非空子集、事件/字段必填、重复关联、被引用拦截）由聚合与应用服务返回，
 * 经 {@code GlobalExceptionHandler} 输出 {@code {code, message, fields?}} 结构。
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/fields")
public class EventFieldController {

    private final EventFieldAppService appService;

    public EventFieldController(EventFieldAppService appService) {
        this.appService = appService;
    }

    /** 列出某事件下的事件字段（R4.1）。 */
    @GetMapping
    public List<EventFieldView> list(@PathVariable("eventCode") String eventCode) {
        return appService.list(eventCode);
    }

    /** 从字段库添加一个全局字段到事件下（R4.2/R4.3/R4.4）。 */
    @PostMapping
    public EventFieldView add(@PathVariable("eventCode") String eventCode,
                              @RequestBody AddEventFieldRequest req) {
        return appService.add(eventCode, req.fieldId(),
                parsePurposes(req.purposes()), req.derived() != null && req.derived());
    }

    /** 标记/取消事件字段的衍生字段标记（R4.5）。 */
    @PutMapping("/{eventFieldId}/derived")
    public EventFieldView markDerived(@PathVariable("eventCode") String eventCode,
                                      @PathVariable("eventFieldId") Long eventFieldId,
                                      @RequestBody MarkDerivedRequest req) {
        return appService.markDerived(eventFieldId, req.derived() != null && req.derived());
    }

    /** 移除事件字段（R4.6/R4.7）。 */
    @DeleteMapping("/{eventFieldId}")
    public void remove(@PathVariable("eventCode") String eventCode,
                       @PathVariable("eventFieldId") Long eventFieldId) {
        appService.remove(eventFieldId);
    }

    /**
     * 将请求中的用途字符串解析为 {@link EventPurpose} 集合。
     *
     * <p>非法用途取值返回字段级校验错误（{@code purposes}），与聚合的非空子集校验一致语义（R4.3）。
     */
    private Set<EventPurpose> parsePurposes(List<String> raw) {
        Set<EventPurpose> purposes = new LinkedHashSet<>();
        if (raw == null) {
            return purposes;
        }
        for (String name : raw) {
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                purposes.add(EventPurpose.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw ValidationException.builder()
                        .field("purposes", "非法的事件字段用途: " + name)
                        .build();
            }
        }
        return purposes;
    }

    /** 从字段库添加请求：字段库字段 id、用途多选、是否衍生。 */
    public record AddEventFieldRequest(@NotNull Long fieldId, List<String> purposes, Boolean derived) {
    }

    /** 衍生标记请求。 */
    public record MarkDerivedRequest(Boolean derived) {
    }
}
