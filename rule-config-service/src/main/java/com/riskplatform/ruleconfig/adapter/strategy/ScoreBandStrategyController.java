package com.riskplatform.ruleconfig.adapter.strategy;

import com.riskplatform.ruleconfig.application.strategy.RuleStrategyAppService;
import com.riskplatform.ruleconfig.domain.strategy.ScoreBandStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评分区间-策略绑定 REST 适配器（R3，配合 R1.3）。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/v1/score-bands/{id}/strategies 全量替换评分区间的策略绑定</li>
 *   <li>GET  /api/v1/score-bands/{id}/strategies 查询评分区间的策略绑定</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/score-bands/{scoreBandId}/strategies")
public class ScoreBandStrategyController {

    private final RuleStrategyAppService appService;

    public ScoreBandStrategyController(RuleStrategyAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public List<ScoreBandStrategyView> bind(@PathVariable Long scoreBandId,
                                            @Valid @RequestBody BindRequest req) {
        return appService.bindScoreBandStrategies(scoreBandId, req.strategyDefIds()).stream()
                .map(ScoreBandStrategyView::from).toList();
    }

    @GetMapping
    public List<ScoreBandStrategyView> list(@PathVariable Long scoreBandId) {
        return appService.listScoreBandStrategies(scoreBandId).stream()
                .map(ScoreBandStrategyView::from).toList();
    }

    /** 绑定请求（全量替换）。 */
    public record BindRequest(@NotNull List<Long> strategyDefIds) {
    }

    /** 视图对象。 */
    public record ScoreBandStrategyView(Long id, Long scoreBandId, Long strategyDefId) {
        static ScoreBandStrategyView from(ScoreBandStrategy s) {
            return new ScoreBandStrategyView(s.getId(), s.getScoreBandId(), s.getStrategyDefId());
        }
    }
}
