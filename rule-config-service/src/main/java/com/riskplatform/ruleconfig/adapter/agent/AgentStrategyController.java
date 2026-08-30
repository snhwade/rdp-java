package com.riskplatform.ruleconfig.adapter.agent;

import com.riskplatform.ruleconfig.application.agent.AgentStrategyAppService;
import com.riskplatform.ruleconfig.application.agent.AgentStrategyAppService.AdoptionAuditView;
import com.riskplatform.ruleconfig.application.agent.AgentStrategyAppService.AgentStrategyView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
 * AI Agent 策略配置（可配置工具链 + 规则 + 可选 LLM + 采纳模式 IA1）。
 */
@RestController
@RequestMapping("/api/v1/agent-strategies")
public class AgentStrategyController {

    private final AgentStrategyAppService appService;

    public AgentStrategyController(AgentStrategyAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    public List<AgentStrategyView> list() {
        return appService.list();
    }

    @GetMapping("/resolve")
    public AgentStrategyView resolve(@RequestParam("eventTypeCode") String eventTypeCode) {
        return appService.resolve(eventTypeCode);
    }

    @GetMapping("/{id}")
    public AgentStrategyView get(@PathVariable long id) {
        return appService.get(id);
    }

    @GetMapping("/{id}/adoption-audits")
    public List<AdoptionAuditView> adoptionAudits(
            @PathVariable long id,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return appService.listAdoptionAudits(id, limit);
    }

    @PostMapping
    public AgentStrategyView create(@Valid @RequestBody CreateRequest req) {
        return appService.create(req.code(), req.name(), req.eventTypeCodes(), req.configJson(),
                req.description(), req.adoptionMode());
    }

    @PutMapping("/{id}")
    public AgentStrategyView update(@PathVariable long id, @Valid @RequestBody UpdateRequest req) {
        return appService.update(id, req.name(), req.eventTypeCodes(), req.configJson(),
                req.enabled(), req.description(), req.adoptionMode());
    }

    public record CreateRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotEmpty List<String> eventTypeCodes,
            String configJson,
            String description,
            String adoptionMode) {
    }

    public record UpdateRequest(
            @NotBlank String name,
            @NotEmpty List<String> eventTypeCodes,
            String configJson,
            Boolean enabled,
            String description,
            String adoptionMode) {
    }
}
