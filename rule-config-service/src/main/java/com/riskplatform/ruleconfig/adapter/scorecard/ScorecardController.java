package com.riskplatform.ruleconfig.adapter.scorecard;

import com.riskplatform.ruleconfig.application.scorecard.ScorecardAppService;
import com.riskplatform.ruleconfig.domain.scorecard.Scorecard;
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
 * 评分卡 REST 适配器（S3）。
 *
 * <ul>
 *   <li>POST   /api/v1/scorecards      新建</li>
 *   <li>GET    /api/v1/scorecards?eventTypeCode= 列表（供引擎执行加载）</li>
 *   <li>GET    /api/v1/scorecards/{id} 详情</li>
 *   <li>PUT    /api/v1/scorecards/{id} 更新</li>
 *   <li>DELETE /api/v1/scorecards/{id} 删除</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/scorecards")
public class ScorecardController {

    private final ScorecardAppService appService;

    public ScorecardController(ScorecardAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Scorecard create(@RequestBody CreateRequest req) {
        return appService.create(req.name(), req.eventTypeCode(), req.variables(), req.levels());
    }

    @GetMapping
    public List<Scorecard> list(@RequestParam(name = "eventTypeCode", required = false) String eventTypeCode) {
        return appService.list(eventTypeCode);
    }

    @GetMapping("/{id}")
    public Scorecard get(@PathVariable("id") Long id) {
        return appService.get(id);
    }

    @PutMapping("/{id}")
    public Scorecard update(@PathVariable("id") Long id, @RequestBody UpdateRequest req) {
        return appService.update(id, req.name(), req.variables(), req.levels(),
                req.status() == null ? "ENABLED" : req.status());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        appService.delete(id);
    }

    public record CreateRequest(
            @NotBlank String name,
            @NotBlank String eventTypeCode,
            @NotEmpty List<Scorecard.Variable> variables,
            @NotEmpty List<Scorecard.Level> levels) {
    }

    public record UpdateRequest(
            @NotBlank String name,
            @NotEmpty List<Scorecard.Variable> variables,
            @NotEmpty List<Scorecard.Level> levels,
            String status) {
    }
}
