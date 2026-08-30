package com.riskplatform.ruleconfig.adapter.strategy;

import com.riskplatform.ruleconfig.application.strategy.VerifyStrategyAppService;
import com.riskplatform.ruleconfig.application.strategy.VerifyStrategyAppService.VerifyStrategyRelations;
import com.riskplatform.ruleconfig.domain.strategy.RuleStrategy;
import com.riskplatform.ruleconfig.domain.strategy.ScoreBandStrategy;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDef;
import com.riskplatform.ruleconfig.domain.strategy.StrategyScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 验证策略 REST 适配器（risk-console-redesign / R5.2-R5.8）。
 *
 * <p>本控制器仅服务于「验证策略（VERIFY）」，不提供其它策略类别的创建入口（R5.2）。
 * 端点：
 * <ul>
 *   <li>GET  /api/v1/verify-strategies 列表（仅 VERIFY）</li>
 *   <li>GET  /api/v1/verify-strategies/{id} 详情</li>
 *   <li>POST /api/v1/verify-strategies 创建（优先级范围、code 唯一、作用域校验）</li>
 *   <li>PUT  /api/v1/verify-strategies/{id} 编辑</li>
 *   <li>GET  /api/v1/verify-strategies/{id}/relations 关联关系（规则 + 评分区间绑定）</li>
 * </ul>
 *
 * <p>验证策略（VERIFY）固定为全场景通用：请求体中的 {@code anyScope}/{@code scopeScenarioId}
 * 将被忽略，持久化时始终写入「不限业务场景」。
 */
@RestController
@RequestMapping("/api/v1/verify-strategies")
public class VerifyStrategyController {

    private final VerifyStrategyAppService appService;

    public VerifyStrategyController(VerifyStrategyAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    public List<VerifyStrategyView> list() {
        return appService.list().stream().map(VerifyStrategyView::from).toList();
    }

    @GetMapping("/{id}")
    public VerifyStrategyView get(@PathVariable Long id) {
        return VerifyStrategyView.from(appService.get(id));
    }

    @PostMapping
    public VerifyStrategyView create(@Valid @RequestBody CreateVerifyStrategyRequest req) {
        StrategyDef def = appService.create(req.code(), req.name(), req.priority(),
                toScope(req.anyScope(), req.scopeScenarioId()), req.paramsJson());
        return VerifyStrategyView.from(def);
    }

    @PutMapping("/{id}")
    public VerifyStrategyView update(@PathVariable Long id,
                                     @Valid @RequestBody UpdateVerifyStrategyRequest req) {
        StrategyDef def = appService.update(id, req.name(), req.priority(),
                toScope(req.anyScope(), req.scopeScenarioId()), req.paramsJson());
        return VerifyStrategyView.from(def);
    }

    @GetMapping("/{id}/relations")
    public VerifyStrategyRelationsView relations(@PathVariable Long id) {
        return VerifyStrategyRelationsView.from(appService.relations(id));
    }

    /** 验证策略固定为全场景通用，忽略请求中的作用域字段。 */
    private StrategyScope toScope(Boolean anyScope, Long scopeScenarioId) {
        return StrategyScope.anyScenario();
    }

    /** 创建请求。优先级范围、作用域必填等不变式由领域层校验并返回字段级错误。 */
    public record CreateVerifyStrategyRequest(@NotBlank String code, @NotBlank String name,
                                              Integer priority, Boolean anyScope,
                                              Long scopeScenarioId, String paramsJson) {
    }

    /** 编辑请求（不可改 code/category）。 */
    public record UpdateVerifyStrategyRequest(@NotBlank String name, Integer priority,
                                              Boolean anyScope, Long scopeScenarioId, String paramsJson) {
    }

    /** 验证策略视图对象。 */
    public record VerifyStrategyView(Long id, String code, String name, Integer priority,
                                     boolean anyScope, Long scopeScenarioId,
                                     String paramsJson, String status) {
        static VerifyStrategyView from(StrategyDef s) {
            StrategyScope scope = s.getScope();
            boolean any = scope != null && scope.isAnyScope();
            Long scenarioId = scope == null ? null : scope.getScenarioId();
            return new VerifyStrategyView(s.getId(), s.getCode(), s.getName(), s.getPriority(),
                    any, scenarioId, s.getParamsJson(), s.getStatus().name());
        }
    }

    /** 关联关系视图（R5.8）：引用该策略的规则绑定与评分区间绑定。 */
    public record VerifyStrategyRelationsView(Long strategyDefId,
                                              List<RuleBindingView> ruleBindings,
                                              List<ScoreBandBindingView> scoreBandBindings) {
        static VerifyStrategyRelationsView from(VerifyStrategyRelations r) {
            return new VerifyStrategyRelationsView(
                    r.strategyDefId(),
                    r.ruleBindings().stream().map(RuleBindingView::from).toList(),
                    r.scoreBandBindings().stream().map(ScoreBandBindingView::from).toList());
        }
    }

    /** 引用该策略的规则绑定视图。 */
    public record RuleBindingView(Long id, Long ruleV2Id, Integer priority) {
        static RuleBindingView from(RuleStrategy rs) {
            return new RuleBindingView(rs.getId(), rs.getRuleV2Id(), rs.getPriority());
        }
    }

    /** 引用该策略的评分区间绑定视图。 */
    public record ScoreBandBindingView(Long id, Long scoreBandId) {
        static ScoreBandBindingView from(ScoreBandStrategy s) {
            return new ScoreBandBindingView(s.getId(), s.getScoreBandId());
        }
    }
}
