package com.riskplatform.ruleconfig.adapter.decisiontree;

import com.riskplatform.ruleconfig.application.decisiontree.DecisionTreeAppService;
import com.riskplatform.ruleconfig.domain.decisiontree.DecisionTree;
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
 * 决策树 REST 适配器（S8）。
 *
 * <ul>
 *   <li>POST   /api/v1/decision-trees      新建</li>
 *   <li>GET    /api/v1/decision-trees?eventTypeCode= 列表</li>
 *   <li>GET    /api/v1/decision-trees/{id} 详情</li>
 *   <li>PUT    /api/v1/decision-trees/{id} 更新</li>
 *   <li>DELETE /api/v1/decision-trees/{id} 删除</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/decision-trees")
public class DecisionTreeController {

    private final DecisionTreeAppService appService;

    public DecisionTreeController(DecisionTreeAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public DecisionTree create(@RequestBody CreateRequest req) {
        return appService.create(req.name(), req.eventTypeCode(), req.rootNodeId(), req.nodes());
    }

    @GetMapping
    public List<DecisionTree> list(@RequestParam(name = "eventTypeCode", required = false) String eventTypeCode) {
        return appService.list(eventTypeCode);
    }

    @GetMapping("/{id}")
    public DecisionTree get(@PathVariable("id") Long id) {
        return appService.get(id);
    }

    @PutMapping("/{id}")
    public DecisionTree update(@PathVariable("id") Long id, @RequestBody UpdateRequest req) {
        return appService.update(id, req.name(), req.rootNodeId(), req.nodes(),
                req.status() == null ? "ENABLED" : req.status());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        appService.delete(id);
    }

    public record CreateRequest(
            @NotBlank String name,
            @NotBlank String eventTypeCode,
            @NotBlank String rootNodeId,
            @NotEmpty List<DecisionTree.Node> nodes) {
    }

    public record UpdateRequest(
            @NotBlank String name,
            @NotBlank String rootNodeId,
            @NotEmpty List<DecisionTree.Node> nodes,
            String status) {
    }
}
