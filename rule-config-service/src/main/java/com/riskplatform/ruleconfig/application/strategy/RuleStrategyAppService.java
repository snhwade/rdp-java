package com.riskplatform.ruleconfig.application.strategy;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.strategy.RuleStrategy;
import com.riskplatform.ruleconfig.domain.strategy.RuleStrategyRepository;
import com.riskplatform.ruleconfig.domain.strategy.ScoreBandStrategy;
import com.riskplatform.ruleconfig.domain.strategy.ScoreBandStrategyRepository;
import com.riskplatform.ruleconfig.domain.strategy.StrategyCategory;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDef;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDefRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则/评分区间-策略绑定应用服务（R3.1/R3.5，配合 R1.3）。
 *
 * <p>职责：
 * <ul>
 *   <li>将一组策略绑定到结构化规则（rule_v2），验证策略带优先级（R3.1）；
 *       绑定时按被绑定策略的 category 校验「验证策略必须带优先级」，
 *       且单规则至多一个验证策略（R3.1：仅允许一个验证策略）。</li>
 *   <li>将一组策略绑定到评分模式分值区间（rule_package_score_band）。</li>
 * </ul>
 *
 * <p>绑定均为「全量替换」语义。通过 {@code @Service} 组件扫描自注册。
 */
@Service
public class RuleStrategyAppService {

    private final RuleStrategyRepository ruleStrategyRepository;
    private final ScoreBandStrategyRepository scoreBandStrategyRepository;
    private final StrategyDefRepository strategyDefRepository;

    public RuleStrategyAppService(RuleStrategyRepository ruleStrategyRepository,
                                  ScoreBandStrategyRepository scoreBandStrategyRepository,
                                  StrategyDefRepository strategyDefRepository) {
        this.ruleStrategyRepository = ruleStrategyRepository;
        this.scoreBandStrategyRepository = scoreBandStrategyRepository;
        this.strategyDefRepository = strategyDefRepository;
    }

    /**
     * 全量替换某规则的策略绑定（R3.1/R3.5）。
     *
     * @param ruleV2Id 结构化规则 ID
     * @param bindings 绑定项列表（策略定义 ID + 可选优先级 + 附加参数）
     * @return 持久化后的绑定列表
     */
    public List<RuleStrategy> bindRuleStrategies(Long ruleV2Id, List<BindRuleStrategyCommand> bindings) {
        List<RuleStrategy> domainBindings = new ArrayList<>();
        int verifyCount = 0;
        for (BindRuleStrategyCommand cmd : bindings) {
            StrategyDef def = strategyDefRepository.findById(cmd.strategyDefId())
                    .orElseThrow(() -> BizException.notFound("策略不存在: " + cmd.strategyDefId()));
            if (def.getCategory() == StrategyCategory.VERIFY) {
                verifyCount++;
                if (verifyCount > 1) {
                    // R3.1：单规则仅允许一个验证策略
                    throw BizException.invalidState("规则仅允许绑定一个验证策略");
                }
            }
            // 领域层按 category 校验「验证策略必须带优先级」
            domainBindings.add(RuleStrategy.create(ruleV2Id, def.getId(), def.getCategory(),
                    cmd.priority(), cmd.extraJson()));
        }
        ruleStrategyRepository.replaceByRuleV2Id(ruleV2Id, domainBindings);
        return domainBindings;
    }

    /** 查询某规则的策略绑定列表。 */
    public List<RuleStrategy> listRuleStrategies(Long ruleV2Id) {
        return ruleStrategyRepository.findByRuleV2Id(ruleV2Id);
    }

    /**
     * 全量替换某评分区间的策略绑定（R3，配合 R1.3）。
     *
     * @param scoreBandId    评分分值区间 ID
     * @param strategyDefIds 策略定义 ID 列表
     * @return 持久化后的绑定列表
     */
    public List<ScoreBandStrategy> bindScoreBandStrategies(Long scoreBandId, List<Long> strategyDefIds) {
        List<ScoreBandStrategy> domainBindings = new ArrayList<>();
        for (Long defId : strategyDefIds) {
            strategyDefRepository.findById(defId)
                    .orElseThrow(() -> BizException.notFound("策略不存在: " + defId));
            domainBindings.add(ScoreBandStrategy.create(scoreBandId, defId));
        }
        scoreBandStrategyRepository.replaceByScoreBandId(scoreBandId, domainBindings);
        return domainBindings;
    }

    /** 查询某评分区间的策略绑定列表。 */
    public List<ScoreBandStrategy> listScoreBandStrategies(Long scoreBandId) {
        return scoreBandStrategyRepository.findByScoreBandId(scoreBandId);
    }

    /** 规则-策略绑定命令。 */
    public record BindRuleStrategyCommand(Long strategyDefId, Integer priority, String extraJson) {
    }
}
