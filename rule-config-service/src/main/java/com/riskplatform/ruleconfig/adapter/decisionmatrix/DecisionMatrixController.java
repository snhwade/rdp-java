package com.riskplatform.ruleconfig.adapter.decisionmatrix;

import com.riskplatform.ruleconfig.application.decisionmatrix.DecisionMatrixAppService;
import com.riskplatform.ruleconfig.domain.decisionmatrix.DecisionMatrix;
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
 * 决策矩阵 REST 适配器（S9）。
 *
 * <ul>
 *   <li>POST   /api/v1/decision-matrices      新建</li>
 *   <li>GET    /api/v1/decision-matrices?eventTypeCode= 列表</li>
 *   <li>GET    /api/v1/decision-matrices/{id} 详情</li>
 *   <li>PUT    /api/v1/decision-matrices/{id} 更新</li>
 *   <li>DELETE /api/v1/decision-matrices/{id} 删除</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/decision-matrices")
public class DecisionMatrixController {

    private final DecisionMatrixAppService appService;

    public DecisionMatrixController(DecisionMatrixAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public DecisionMatrix create(@RequestBody CreateRequest req) {
        return appService.create(req.name(), req.eventTypeCode(), req.rowVar(), req.rowBins(),
                req.colVar(), req.colBins(), req.cells());
    }

    @GetMapping
    public List<DecisionMatrix> list(@RequestParam(name = "eventTypeCode", required = false) String eventTypeCode) {
        return appService.list(eventTypeCode);
    }

    @GetMapping("/{id}")
    public DecisionMatrix get(@PathVariable("id") Long id) {
        return appService.get(id);
    }

    @PutMapping("/{id}")
    public DecisionMatrix update(@PathVariable("id") Long id, @RequestBody UpdateRequest req) {
        return appService.update(id, req.name(), req.rowVar(), req.rowBins(),
                req.colVar(), req.colBins(), req.cells(), req.status() == null ? "ENABLED" : req.status());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        appService.delete(id);
    }

    public record CreateRequest(
            @NotBlank String name,
            @NotBlank String eventTypeCode,
            @NotBlank String rowVar,
            @NotEmpty List<DecisionMatrix.Bin> rowBins,
            @NotBlank String colVar,
            @NotEmpty List<DecisionMatrix.Bin> colBins,
            @NotEmpty List<DecisionMatrix.Cell> cells) {
    }

    public record UpdateRequest(
            @NotBlank String name,
            @NotBlank String rowVar,
            @NotEmpty List<DecisionMatrix.Bin> rowBins,
            @NotBlank String colVar,
            @NotEmpty List<DecisionMatrix.Bin> colBins,
            @NotEmpty List<DecisionMatrix.Cell> cells,
            String status) {
    }
}
