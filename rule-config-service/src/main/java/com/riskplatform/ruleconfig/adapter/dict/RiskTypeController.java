package com.riskplatform.ruleconfig.adapter.dict;

import com.riskplatform.ruleconfig.application.dict.DictAppService;
import com.riskplatform.ruleconfig.domain.dict.RiskType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风险类型字典 REST 适配器（R12.1/R12.4）。
 *
 * <ul>
 *   <li>POST   /api/v1/risk-types      创建</li>
 *   <li>GET    /api/v1/risk-types      列表</li>
 *   <li>PUT    /api/v1/risk-types/{id} 更新</li>
 *   <li>DELETE /api/v1/risk-types/{id} 删除（引用校验）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/risk-types")
public class RiskTypeController {

    private final DictAppService appService;

    public RiskTypeController(DictAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public RiskTypeView create(@Valid @RequestBody CreateRequest req) {
        return RiskTypeView.from(appService.createRiskType(req.code(), req.name()));
    }

    @GetMapping
    public List<RiskTypeView> list() {
        return appService.listRiskTypes().stream().map(RiskTypeView::from).toList();
    }

    @PutMapping("/{id}")
    public RiskTypeView update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return RiskTypeView.from(appService.updateRiskType(id, req.name(), req.status()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        appService.deleteRiskType(id);
    }

    /** 创建请求。 */
    public record CreateRequest(@NotBlank String code, @NotBlank String name) {
    }

    /** 更新请求。 */
    public record UpdateRequest(@NotBlank String name, String status) {
    }

    /** 视图对象。 */
    public record RiskTypeView(Long id, String code, String name, String status) {
        static RiskTypeView from(RiskType e) {
            return new RiskTypeView(e.getId(), e.getCode(), e.getName(), e.getStatus().name());
        }
    }
}
