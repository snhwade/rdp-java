package com.riskplatform.ruleconfig.adapter.dict;

import com.riskplatform.ruleconfig.application.dict.DictAppService;
import com.riskplatform.ruleconfig.domain.dict.RiskLevel;
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
 * 风险等级字典 REST 适配器（R12.1/R12.4）。
 *
 * <ul>
 *   <li>POST   /api/v1/risk-levels      创建</li>
 *   <li>GET    /api/v1/risk-levels      列表（按 order_no 升序）</li>
 *   <li>PUT    /api/v1/risk-levels/{id} 更新</li>
 *   <li>DELETE /api/v1/risk-levels/{id} 删除（引用校验）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/risk-levels")
public class RiskLevelController {

    private final DictAppService appService;

    public RiskLevelController(DictAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public RiskLevelView create(@Valid @RequestBody CreateRequest req) {
        return RiskLevelView.from(appService.createRiskLevel(req.code(), req.name(), nz(req.orderNo())));
    }

    @GetMapping
    public List<RiskLevelView> list() {
        return appService.listRiskLevels().stream().map(RiskLevelView::from).toList();
    }

    @PutMapping("/{id}")
    public RiskLevelView update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return RiskLevelView.from(appService.updateRiskLevel(id, req.name(), nz(req.orderNo()), req.status()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        appService.deleteRiskLevel(id);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /** 创建请求。 */
    public record CreateRequest(@NotBlank String code, @NotBlank String name, Integer orderNo) {
    }

    /** 更新请求。 */
    public record UpdateRequest(@NotBlank String name, Integer orderNo, String status) {
    }

    /** 视图对象。 */
    public record RiskLevelView(Long id, String code, String name, int orderNo, String status) {
        static RiskLevelView from(RiskLevel e) {
            return new RiskLevelView(e.getId(), e.getCode(), e.getName(), e.getOrderNo(), e.getStatus().name());
        }
    }
}
