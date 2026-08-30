package com.riskplatform.ruleconfig.adapter.strategy;

import com.riskplatform.ruleconfig.application.strategy.StrategyDefAppService;
import com.riskplatform.ruleconfig.domain.strategy.StrategyCategory;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * 策略定义 REST 适配器（R3.1/R3.2/R3.3/R3.4）。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/v1/strategies 创建策略定义</li>
 *   <li>GET  /api/v1/strategies 列表（可选 category 过滤）</li>
 *   <li>GET  /api/v1/strategies/{id} 详情</li>
 *   <li>PUT  /api/v1/strategies/{id} 更新名称与参数</li>
 *   <li>PUT  /api/v1/strategies/{id}/status 启用/禁用</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyController {

    private final StrategyDefAppService appService;

    public StrategyController(StrategyDefAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public StrategyView create(@Valid @RequestBody CreateStrategyRequest req) {
        return StrategyView.from(appService.create(req.category(), req.code(), req.name(), req.paramsJson()));
    }

    @GetMapping
    public List<StrategyView> list(@RequestParam(required = false) StrategyCategory category) {
        return appService.list(category).stream().map(StrategyView::from).toList();
    }

    @GetMapping("/{id}")
    public StrategyView get(@PathVariable Long id) {
        return StrategyView.from(appService.get(id));
    }

    @PutMapping("/{id}")
    public StrategyView update(@PathVariable Long id, @Valid @RequestBody UpdateStrategyRequest req) {
        return StrategyView.from(appService.update(id, req.name(), req.paramsJson()));
    }

    @PutMapping("/{id}/status")
    public StrategyView setStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        return StrategyView.from(appService.setStatus(id, enabled));
    }

    /** 创建请求。基础非空校验由 Bean Validation 完成，长度/字符集校验在领域层。 */
    public record CreateStrategyRequest(@NotNull StrategyCategory category, @NotBlank String code,
                                        @NotBlank String name, String paramsJson) {
    }

    /** 更新请求（不可改 code/category）。 */
    public record UpdateStrategyRequest(@NotBlank String name, String paramsJson) {
    }

    /** 视图对象。 */
    public record StrategyView(Long id, String category, String code, String name,
                               String paramsJson, String status) {
        static StrategyView from(StrategyDef s) {
            return new StrategyView(s.getId(), s.getCategory().name(), s.getCode(), s.getName(),
                    s.getParamsJson(), s.getStatus().name());
        }
    }
}
