package com.riskplatform.ruleconfig.adapter.decisiontable;

import com.riskplatform.ruleconfig.application.decisiontable.DecisionTableAppService;
import com.riskplatform.ruleconfig.domain.decisiontable.DecisionTable;
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
 * 决策表 REST 适配器（S2）。
 *
 * <ul>
 *   <li>POST   /api/v1/decision-tables      新建</li>
 *   <li>GET    /api/v1/decision-tables?eventTypeCode= 列表（供引擎执行加载）</li>
 *   <li>GET    /api/v1/decision-tables/{id} 详情</li>
 *   <li>PUT    /api/v1/decision-tables/{id} 更新</li>
 *   <li>DELETE /api/v1/decision-tables/{id} 删除</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/decision-tables")
public class DecisionTableController {

    private final DecisionTableAppService appService;

    public DecisionTableController(DecisionTableAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public DecisionTable create(@org.springframework.web.bind.annotation.RequestBody CreateRequest req) {
        return appService.create(req.name(), req.eventTypeCode(),
                DecisionTable.HitPolicy.valueOf(req.hitPolicy()), req.columns(), req.rows());
    }

    @GetMapping
    public List<DecisionTable> list(@RequestParam(name = "eventTypeCode", required = false) String eventTypeCode) {
        return appService.list(eventTypeCode);
    }

    @GetMapping("/{id}")
    public DecisionTable get(@PathVariable("id") Long id) {
        return appService.get(id);
    }

    @PutMapping("/{id}")
    public DecisionTable update(@PathVariable("id") Long id, @RequestBody UpdateRequest req) {
        return appService.update(id, req.name(),
                DecisionTable.HitPolicy.valueOf(req.hitPolicy()), req.columns(), req.rows(),
                req.status() == null ? "ENABLED" : req.status());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        appService.delete(id);
    }

    public record CreateRequest(
            @NotBlank String name,
            @NotBlank String eventTypeCode,
            @NotBlank String hitPolicy,
            @NotEmpty List<DecisionTable.Column> columns,
            @NotEmpty List<DecisionTable.Row> rows) {
    }

    public record UpdateRequest(
            @NotBlank String name,
            @NotBlank String hitPolicy,
            @NotEmpty List<DecisionTable.Column> columns,
            @NotEmpty List<DecisionTable.Row> rows,
            String status) {
    }
}
