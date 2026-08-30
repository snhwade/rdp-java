package com.riskplatform.ruleconfig.adapter.strategy;

import com.riskplatform.ruleconfig.application.strategy.RuleStrategyAppService;
import com.riskplatform.ruleconfig.application.strategy.RuleStrategyAppService.BindRuleStrategyCommand;
import com.riskplatform.ruleconfig.domain.strategy.RuleStrategy;
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
 * 规则-策略绑定 REST 适配器（R3.1/R3.5）。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/v1/rules-v2/{id}/strategies 全量替换规则的策略绑定（验证策略带优先级）</li>
 *   <li>GET  /api/v1/rules-v2/{id}/strategies 查询规则的策略绑定</li>
 * </ul>
 *
 * <p>仅新建本子域的 Controller，不修改既有 rules-v2 控制器。
 */
@RestController
@RequestMapping("/api/v1/rules-v2/{ruleV2Id}/strategies")
public class RuleStrategyController {

    private final RuleStrategyAppService appService;

    public RuleStrategyController(RuleStrategyAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public List<RuleStrategyView> bind(@PathVariable Long ruleV2Id,
                                       @Valid @RequestBody BindRequest req) {
        List<BindRuleStrategyCommand> commands = req.bindings().stream()
                .map(b -> new BindRuleStrategyCommand(b.strategyDefId(), b.priority(), b.extraJson()))
                .toList();
        return appService.bindRuleStrategies(ruleV2Id, commands).stream()
                .map(RuleStrategyView::from).toList();
    }

    @GetMapping
    public List<RuleStrategyView> list(@PathVariable Long ruleV2Id) {
        return appService.listRuleStrategies(ruleV2Id).stream().map(RuleStrategyView::from).toList();
    }

    /** 绑定请求（全量替换）。 */
    public record BindRequest(@NotNull List<BindingItem> bindings) {
    }

    /** 单个绑定项。priority 仅验证策略必填（领域层按类别校验）。 */
    public record BindingItem(@NotNull Long strategyDefId, Integer priority, String extraJson) {
    }

    /** 视图对象。 */
    public record RuleStrategyView(Long id, Long ruleV2Id, Long strategyDefId,
                                   Integer priority, String extraJson) {
        static RuleStrategyView from(RuleStrategy rs) {
            return new RuleStrategyView(rs.getId(), rs.getRuleV2Id(), rs.getStrategyDefId(),
                    rs.getPriority(), rs.getExtraJson());
        }
    }
}
