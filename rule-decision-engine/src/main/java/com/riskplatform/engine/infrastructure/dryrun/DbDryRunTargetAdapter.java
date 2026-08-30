package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.dryrun.DryRunTargetPort;
import com.riskplatform.engine.domain.dryrun.DryRunTargetType;
import com.riskplatform.engine.domain.rulepackage.RulePackageDefinition;
import com.riskplatform.engine.domain.rulepackage.RulePackageRule;
import com.riskplatform.engine.domain.rulepackage.RuleRuntimeStatus;
import com.riskplatform.engine.domain.rulepackage.TriggerMode;
import com.riskplatform.engine.domain.rulepackage.WarnScoreOp;
import com.riskplatform.engine.domain.score.ScoreDynamicBand;
import com.riskplatform.engine.domain.score.ScoreRule;
import com.riskplatform.engine.domain.strategy.ScoreBand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 试运行目标定义加载适配器（R5.2/R5.4）。
 *
 * <p>从 MySQL（rule-config 拥有的表，引擎共享同一库）加载目标执行定义，统一为
 * {@link RulePackageDefinition} 供 {@code RulePackageExecutor} 复用：
 * <ul>
 *   <li>{@link DryRunTargetType#RULE}：读 rule_v2 + rule_dynamic_score，包装成「单规则命中模式包」；</li>
 *   <li>{@link DryRunTargetType#RULE_PACKAGE}：读 rule_package + rule_package_rule + 各规则定义
 *       + rule_package_score_band，按触发模式装配命中/评分定义。</li>
 * </ul>
 *
 * <p>注意：本适配器<strong>只读取配置定义</strong>，不调用任何在线决策组件，不写任何在线表，
 * 与试运行影子模式的隔离约束一致（R5.2/R5.6）。
 *
 * <p>策略绑定（{@code strategies}）当前未在影子统计中使用（试运行不输出真实策略 R5.2），
 * 故规则策略列表置空，仅装配命中/触发判定与评分所需字段，避免无谓加载。
 */
@Component
public class DbDryRunTargetAdapter implements DryRunTargetPort {

    private static final Logger log = LoggerFactory.getLogger(DbDryRunTargetAdapter.class);

    /** 单规则试运行的默认包内优先级（仅一条规则，优先级无实际比较意义）。 */
    private static final int SINGLE_RULE_PRIORITY = 0;

    private final RuleV2SampleMapper ruleMapper;
    private final RuleDynamicScoreMapper dynamicScoreMapper;
    private final RulePackageReadMapper packageMapper;
    private final RulePackageRuleReadMapper packageRuleMapper;
    private final RulePackageScoreBandReadMapper scoreBandMapper;

    public DbDryRunTargetAdapter(RuleV2SampleMapper ruleMapper,
                                 RuleDynamicScoreMapper dynamicScoreMapper,
                                 RulePackageReadMapper packageMapper,
                                 RulePackageRuleReadMapper packageRuleMapper,
                                 RulePackageScoreBandReadMapper scoreBandMapper) {
        this.ruleMapper = ruleMapper;
        this.dynamicScoreMapper = dynamicScoreMapper;
        this.packageMapper = packageMapper;
        this.packageRuleMapper = packageRuleMapper;
        this.scoreBandMapper = scoreBandMapper;
    }

    @Override
    public RulePackageDefinition load(DryRunTargetType targetType, long targetId) {
        if (targetType == DryRunTargetType.RULE) {
            return loadSingleRule(targetId);
        }
        return loadPackage(targetId);
    }

    /** 单规则 → 单规则命中模式包。 */
    private RulePackageDefinition loadSingleRule(long ruleId) {
        RuleV2SamplePO po = ruleMapper.selectById(ruleId);
        if (po == null) {
            log.warn("试运行目标规则不存在: ruleId={}", ruleId);
            return null;
        }
        // 单规则试运行：按其三态标注 trialRun（显式指定单规则为目标，不在此过滤）
        boolean trialRun = RuleRuntimeStatus.parse(po.getStatus()).trialRun();
        RulePackageRule rule = toPackageRule(po, SINGLE_RULE_PRIORITY, trialRun);
        return RulePackageDefinition.hit(null, List.of(rule));
    }

    /** 规则包 → 按触发模式装配命中/评分定义。 */
    private RulePackageDefinition loadPackage(long packageId) {
        RulePackagePO pkg = packageMapper.selectById(packageId);
        if (pkg == null) {
            log.warn("试运行目标规则包不存在: packageId={}", packageId);
            return null;
        }

        // 包内规则关联（含包内优先级）
        List<RulePackageRulePO> relations = packageRuleMapper.selectList(
                new LambdaQueryWrapper<RulePackageRulePO>()
                        .eq(RulePackageRulePO::getRulePackageId, packageId));

        List<RulePackageRule> rules = new ArrayList<>(relations.size());
        for (RulePackageRulePO rel : relations) {
            RuleV2SamplePO rulePo = ruleMapper.selectById(rel.getRuleV2Id());
            if (rulePo == null) {
                continue;
            }
            // 规则三态过滤：仅 ONLINE/TRIAL_RUN 进入执行集，跳过 OFFLINE（R7.3/R7.4，与在线路径一致）
            RuleRuntimeStatus status = RuleRuntimeStatus.parse(rulePo.getStatus());
            if (!status.executable()) {
                continue;
            }
            int priority = rel.getPriority() == null ? 0 : rel.getPriority();
            rules.add(toPackageRule(rulePo, priority, status.trialRun()));
        }

        TriggerMode mode = parseTriggerMode(pkg.getTriggerMode());
        if (mode == TriggerMode.SCORE) {
            List<ScoreBand> bands = loadScoreBands(packageId);
            WarnScoreOp warnOp = parseWarnOp(pkg.getWarnScoreOp());
            boolean warnEnabled = pkg.getWarnScoreEnabled() != null && pkg.getWarnScoreEnabled() == 1
                    && warnOp != null && pkg.getWarnScoreThreshold() != null;
            if (warnEnabled) {
                return RulePackageDefinition.score(packageId, rules, bands, warnOp,
                        pkg.getWarnScoreThreshold());
            }
            return RulePackageDefinition.score(packageId, rules, bands);
        }
        return RulePackageDefinition.hit(packageId, rules);
    }

    /** rule_v2 → 执行侧 RulePackageRule（含编译表达式、决策语义、评分配置、三态标识）。 */
    private RulePackageRule toPackageRule(RuleV2SamplePO po, int priority, boolean trialRun) {
        boolean shortCircuited = po.getShortCircuited() != null && po.getShortCircuited() == 1;
        Decision decision = riskLevelToDecision(po.getRiskLevelCode());
        ScoreRule scoreRule = loadScoreRule(po);
        return new RulePackageRule(
                po.getId(),
                1,
                priority,
                po.getCompiledExpr(),
                decision,
                shortCircuited,
                List.of(),     // 试运行不输出真实策略（R5.2），策略绑定无需加载
                scoreRule,
                trialRun);
    }

    /** 加载某规则的评分配置（基础分 + 动态分区间）。无动态分时仍带基础分。 */
    private ScoreRule loadScoreRule(RuleV2SamplePO po) {
        List<RuleDynamicScorePO> bands = dynamicScoreMapper.selectList(
                new LambdaQueryWrapper<RuleDynamicScorePO>()
                        .eq(RuleDynamicScorePO::getRuleV2Id, po.getId())
                        .orderByAsc(RuleDynamicScorePO::getOrderNo));
        List<ScoreDynamicBand> dynamicBands = new ArrayList<>(bands.size());
        for (RuleDynamicScorePO b : bands) {
            dynamicBands.add(new ScoreDynamicBand(
                    b.getIndicatorRefName(),
                    b.getLower(),
                    b.getUpper(),
                    b.getLowerInclusive() == null || b.getLowerInclusive() == 1,
                    b.getUpperInclusive() != null && b.getUpperInclusive() == 1,
                    b.getScore()));
        }
        BigDecimal base = po.getBaseScore() == null ? BigDecimal.ZERO : po.getBaseScore();
        return new ScoreRule(po.getId(), base, dynamicBands);
    }

    /** 加载规则包评分分值区间（按 order_no 升序）。 */
    private List<ScoreBand> loadScoreBands(long packageId) {
        List<RulePackageScoreBandPO> pos = scoreBandMapper.selectList(
                new LambdaQueryWrapper<RulePackageScoreBandPO>()
                        .eq(RulePackageScoreBandPO::getRulePackageId, packageId)
                        .orderByAsc(RulePackageScoreBandPO::getOrderNo));
        List<ScoreBand> bands = new ArrayList<>(pos.size());
        for (RulePackageScoreBandPO po : pos) {
            bands.add(new ScoreBand(
                    po.getLower(),
                    po.getUpper(),
                    po.getLowerInclusive() == null || po.getLowerInclusive() == 1,
                    po.getUpperInclusive() != null && po.getUpperInclusive() == 1,
                    po.getRiskLevelCode(),
                    List.of()));     // 试运行不输出真实策略（R5.2），区间策略无需加载
        }
        return bands;
    }

    /**
     * 命中模式规则的命中决策：由规则风险等级编码经默认风险等级映射推导。
     * 无风险等级时按 REVIEW（保守，确保「命中即有处置倾向」）。
     */
    private Decision riskLevelToDecision(String riskLevelCode) {
        if (riskLevelCode == null || riskLevelCode.isBlank()) {
            return Decision.REVIEW;
        }
        String code = riskLevelCode.trim().toUpperCase();
        if (code.startsWith("HIGH") || code.startsWith("REJECT") || code.startsWith("BLOCK")
                || code.equals("H")) {
            return Decision.REJECT;
        }
        if (code.startsWith("LOW") || code.startsWith("PASS") || code.equals("L")) {
            return Decision.PASS;
        }
        return Decision.REVIEW;
    }

    private TriggerMode parseTriggerMode(String v) {
        try {
            return v == null ? TriggerMode.HIT : TriggerMode.valueOf(v);
        } catch (IllegalArgumentException e) {
            return TriggerMode.HIT;
        }
    }

    private WarnScoreOp parseWarnOp(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return WarnScoreOp.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
