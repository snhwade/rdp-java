package com.riskplatform.ruleconfig.application.strategy;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.strategy.RuleStrategy;
import com.riskplatform.ruleconfig.domain.strategy.RuleStrategyRepository;
import com.riskplatform.ruleconfig.domain.strategy.ScoreBandStrategy;
import com.riskplatform.ruleconfig.domain.strategy.ScoreBandStrategyRepository;
import com.riskplatform.ruleconfig.domain.strategy.StrategyCategory;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDef;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDefRepository;
import com.riskplatform.ruleconfig.domain.strategy.StrategyScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 验证策略应用服务（risk-console-redesign / R5.2-R5.8）。
 *
 * <p>本期仅暴露并支持类别为「验证策略（{@link StrategyCategory#VERIFY}）」的策略（R5.2）：
 * 所有读写均限定 category=VERIFY，不提供其它类别的创建入口。其它策略类别由既有
 * {@code StrategyDefAppService} 处理，本服务不触碰。
 *
 * <p>职责：
 * <ul>
 *   <li>列表：仅返回验证策略（R5.1）。</li>
 *   <li>创建：领域层校验优先级范围（R5.6）与作用域（R5.4）；应用层做 code 唯一校验（R5.7，
 *       在 VERIFY 类别内精确等值，无前缀/模糊匹配）。</li>
 *   <li>编辑：更新名称/优先级/作用域/参数并校验。</li>
 *   <li>关联关系查询：返回引用该验证策略的规则绑定与评分区间绑定（R5.8）。</li>
 * </ul>
 *
 * <p>通过 {@code @Service} 组件扫描自注册，避免改动共享装配类。
 */
@Service
public class VerifyStrategyAppService {

    private final StrategyDefRepository strategyDefRepository;
    private final RuleStrategyRepository ruleStrategyRepository;
    private final ScoreBandStrategyRepository scoreBandStrategyRepository;

    public VerifyStrategyAppService(StrategyDefRepository strategyDefRepository,
                                    RuleStrategyRepository ruleStrategyRepository,
                                    ScoreBandStrategyRepository scoreBandStrategyRepository) {
        this.strategyDefRepository = strategyDefRepository;
        this.ruleStrategyRepository = ruleStrategyRepository;
        this.scoreBandStrategyRepository = scoreBandStrategyRepository;
    }

    /** 列出全部验证策略（R5.1，仅 VERIFY），按优先级降序（数值越大越靠前）。 */
    public List<StrategyDef> list() {
        return strategyDefRepository.findByCategory(StrategyCategory.VERIFY).stream()
                .sorted(Comparator.comparing(StrategyDef::getPriority,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** 按 id 查询验证策略；不存在或非 VERIFY 时拒绝。 */
    public StrategyDef get(Long id) {
        return loadVerify(id);
    }

    /**
     * 创建验证策略（R5.3-R5.7）。
     *
     * <p>领域层先校验优先级范围（R5.6）与作用域必填（R5.4）；通过后再做 VERIFY 内 code 唯一校验
     * （R5.7，精确等值）。先校验领域不变式再查重，保证非法请求即使 code 重复也优先暴露字段错误。
     *
     * @param code       策略代码
     * @param name       策略名称
     * @param priority   优先级 1..9999，数值越大优先级越高
     * @param scope      已忽略：验证策略固定为全场景通用
     * @param paramsJson 策略参数 JSON（可空）
     */
    @Transactional
    public StrategyDef create(String code, String name, Integer priority, StrategyScope scope, String paramsJson) {
        StrategyDef def = StrategyDef.createVerify(code, name, priority, StrategyScope.anyScenario(), paramsJson);
        if (strategyDefRepository.existsByCategoryAndCode(StrategyCategory.VERIFY, code)) {
            throw BizException.duplicate("验证策略 code 已存在: " + code);
        }
        return strategyDefRepository.save(def);
    }

    /** 编辑验证策略（R5）：更新名称、优先级、参数；作用域固定为全场景通用。 */
    @Transactional
    public StrategyDef update(Long id, String name, Integer priority, StrategyScope scope, String paramsJson) {
        StrategyDef def = loadVerify(id);
        def.updateVerify(name, priority, StrategyScope.anyScenario(), paramsJson);
        strategyDefRepository.update(def);
        return def;
    }

    /**
     * 查询验证策略的关联关系（R5.8）：引用该策略的规则绑定与评分区间绑定。
     *
     * <p>返回稳定结构；若规则/评分区间引用数据尚未产生则为对应空列表，构成清晰扩展点。
     */
    public VerifyStrategyRelations relations(Long id) {
        StrategyDef def = loadVerify(id);
        List<RuleStrategy> ruleBindings = ruleStrategyRepository.findByStrategyDefId(def.getId());
        List<ScoreBandStrategy> scoreBandBindings =
                scoreBandStrategyRepository.findByStrategyDefId(def.getId());
        return new VerifyStrategyRelations(def.getId(), ruleBindings, scoreBandBindings);
    }

    /** 加载验证策略：不存在或类别非 VERIFY 时拒绝（保证本服务只处理验证策略，R5.2）。 */
    private StrategyDef loadVerify(Long id) {
        StrategyDef def = strategyDefRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("验证策略不存在: " + id));
        if (def.getCategory() != StrategyCategory.VERIFY) {
            throw BizException.notFound("验证策略不存在: " + id);
        }
        return def;
    }

    /**
     * 验证策略关联关系（R5.8）。
     *
     * @param strategyDefId     验证策略 ID
     * @param ruleBindings      引用该策略的规则绑定（rule_strategy）
     * @param scoreBandBindings 引用该策略的评分区间绑定（score_band_strategy）
     */
    public record VerifyStrategyRelations(Long strategyDefId,
                                          List<RuleStrategy> ruleBindings,
                                          List<ScoreBandStrategy> scoreBandBindings) {
    }
}
