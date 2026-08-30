package com.riskplatform.ruleconfig.infrastructure.rulepackage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.rulepackage.ComputeMode;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackage;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackageRepository;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackageStatus;
import com.riskplatform.ruleconfig.domain.rulepackage.ScoreBand;
import com.riskplatform.ruleconfig.domain.rulepackage.TriggerMode;
import com.riskplatform.ruleconfig.domain.rulepackage.WarnScoreOp;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 规则包仓储 MyBatis-Plus 实现（R1）。
 *
 * <p>聚合跨五张表持久化：
 * <ul>
 *   <li>{@code rule_package}（主体）</li>
 *   <li>{@code rule_package_scenario}（归属场景，全量替换）</li>
 *   <li>{@code rule_package_event}（决策事件，全量替换）</li>
 *   <li>{@code rule_package_score_band}（评分分值区间，全量替换）</li>
 *   <li>{@code rule_package_rule}（规则关联，独立增删 associate/dissociate）</li>
 * </ul>
 *
 * <p>关联场景/事件/分值区间采用「全量替换」策略：更新时先删后插，保证与聚合内状态一致。
 * 规则关联（rule_package_rule）支持规则归属多个包（R1.7），由 associateRule/dissociateRule 单独维护。
 */
@Repository
public class RulePackageRepositoryImpl implements RulePackageRepository {

    private final RulePackageMapper packageMapper;
    private final RulePackageScenarioMapper scenarioMapper;
    private final RulePackageEventMapper eventMapper;
    private final RulePackageScoreBandMapper scoreBandMapper;
    private final RulePackageRuleMapper ruleMapper;

    public RulePackageRepositoryImpl(RulePackageMapper packageMapper,
                                     RulePackageScenarioMapper scenarioMapper,
                                     RulePackageEventMapper eventMapper,
                                     RulePackageScoreBandMapper scoreBandMapper,
                                     RulePackageRuleMapper ruleMapper) {
        this.packageMapper = packageMapper;
        this.scenarioMapper = scenarioMapper;
        this.eventMapper = eventMapper;
        this.scoreBandMapper = scoreBandMapper;
        this.ruleMapper = ruleMapper;
    }

    @Override
    @Transactional
    public RulePackage save(RulePackage rulePackage) {
        RulePackagePO po = toPO(rulePackage);
        packageMapper.insert(po);
        rulePackage.assignId(po.getId());
        insertScenarios(po.getId(), rulePackage.getScenarioIds());
        insertEvents(po.getId(), rulePackage.getEventTypeCodes());
        insertScoreBands(po.getId(), rulePackage.getScoreBands());
        return rulePackage;
    }

    @Override
    @Transactional
    public void update(RulePackage rulePackage) {
        packageMapper.updateById(toPO(rulePackage));
        Long id = rulePackage.getId();
        // 全量替换关联场景/事件/分值区间
        scenarioMapper.delete(new LambdaQueryWrapper<RulePackageScenarioPO>()
                .eq(RulePackageScenarioPO::getRulePackageId, id));
        eventMapper.delete(new LambdaQueryWrapper<RulePackageEventPO>()
                .eq(RulePackageEventPO::getRulePackageId, id));
        scoreBandMapper.delete(new LambdaQueryWrapper<RulePackageScoreBandPO>()
                .eq(RulePackageScoreBandPO::getRulePackageId, id));
        insertScenarios(id, rulePackage.getScenarioIds());
        insertEvents(id, rulePackage.getEventTypeCodes());
        insertScoreBands(id, rulePackage.getScoreBands());
    }

    @Override
    public Optional<RulePackage> findById(Long id) {
        return Optional.ofNullable(packageMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<RulePackage> findByCodeAndTriggerMode(String code, TriggerMode triggerMode) {
        RulePackagePO po = packageMapper.selectOne(new LambdaQueryWrapper<RulePackagePO>()
                .eq(RulePackagePO::getCode, code)
                .eq(RulePackagePO::getTriggerMode, triggerMode.name()));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<RulePackage> findAll() {
        return packageMapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public List<RulePackage> findByEventCode(String eventCode) {
        if (eventCode == null || eventCode.isBlank()) {
            return findAll();
        }
        List<Long> pkgIds = eventMapper.selectPackageIdsByEventCode(eventCode);
        if (pkgIds.isEmpty()) {
            return List.of();
        }
        return packageMapper.selectBatchIds(pkgIds).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByTriggerModeAndName(TriggerMode triggerMode, String name, Long selfId) {
        LambdaQueryWrapper<RulePackagePO> wrapper = new LambdaQueryWrapper<RulePackagePO>()
                .eq(RulePackagePO::getTriggerMode, triggerMode.name())
                .eq(RulePackagePO::getName, name);
        if (selfId != null) {
            wrapper.ne(RulePackagePO::getId, selfId);
        }
        return packageMapper.exists(wrapper);
    }

    @Override
    public void associateRule(Long rulePackageId, Long ruleV2Id, int priority) {
        // 先删除旧关联（保证幂等，便于更新优先级），再插入
        ruleMapper.delete(new LambdaQueryWrapper<RulePackageRulePO>()
                .eq(RulePackageRulePO::getRulePackageId, rulePackageId)
                .eq(RulePackageRulePO::getRuleV2Id, ruleV2Id));
        RulePackageRulePO po = new RulePackageRulePO();
        po.setRulePackageId(rulePackageId);
        po.setRuleV2Id(ruleV2Id);
        po.setPriority(priority);
        ruleMapper.insert(po);
    }

    @Override
    public void dissociateRule(Long rulePackageId, Long ruleV2Id) {
        ruleMapper.delete(new LambdaQueryWrapper<RulePackageRulePO>()
                .eq(RulePackageRulePO::getRulePackageId, rulePackageId)
                .eq(RulePackageRulePO::getRuleV2Id, ruleV2Id));
    }

    @Override
    public List<Long> findRuleIds(Long rulePackageId) {
        return ruleMapper.selectRuleIds(rulePackageId);
    }

    // —— 内部辅助：关联表写入 ——

    private void insertScenarios(Long pkgId, List<Long> scenarioIds) {
        for (Long sid : scenarioIds) {
            RulePackageScenarioPO po = new RulePackageScenarioPO();
            po.setRulePackageId(pkgId);
            po.setScenarioId(sid);
            scenarioMapper.insert(po);
        }
    }

    private void insertEvents(Long pkgId, List<String> codes) {
        for (String code : codes) {
            RulePackageEventPO po = new RulePackageEventPO();
            po.setRulePackageId(pkgId);
            po.setEventTypeCode(code);
            eventMapper.insert(po);
        }
    }

    private void insertScoreBands(Long pkgId, List<ScoreBand> bands) {
        for (ScoreBand band : bands) {
            RulePackageScoreBandPO po = new RulePackageScoreBandPO();
            po.setRulePackageId(pkgId);
            po.setLower(band.getLower());
            po.setUpper(band.getUpper());
            po.setLowerInclusive(band.isLowerInclusive() ? 1 : 0);
            po.setUpperInclusive(band.isUpperInclusive() ? 1 : 0);
            po.setRiskLevelCode(band.getRiskLevelCode());
            po.setOrderNo(band.getOrderNo());
            scoreBandMapper.insert(po);
        }
    }

    // —— 内部辅助：关联表读取 ——

    private List<Long> loadScenarioIds(Long pkgId) {
        return scenarioMapper.selectList(new LambdaQueryWrapper<RulePackageScenarioPO>()
                        .eq(RulePackageScenarioPO::getRulePackageId, pkgId))
                .stream().map(RulePackageScenarioPO::getScenarioId).toList();
    }

    private List<String> loadEventCodes(Long pkgId) {
        return eventMapper.selectList(new LambdaQueryWrapper<RulePackageEventPO>()
                        .eq(RulePackageEventPO::getRulePackageId, pkgId))
                .stream().map(RulePackageEventPO::getEventTypeCode).toList();
    }

    private List<ScoreBand> loadScoreBands(Long pkgId) {
        List<RulePackageScoreBandPO> pos = scoreBandMapper.selectList(
                new LambdaQueryWrapper<RulePackageScoreBandPO>()
                        .eq(RulePackageScoreBandPO::getRulePackageId, pkgId)
                        .orderByAsc(RulePackageScoreBandPO::getOrderNo));
        List<ScoreBand> bands = new ArrayList<>(pos.size());
        for (RulePackageScoreBandPO po : pos) {
            bands.add(ScoreBand.of(po.getLower(), po.getUpper(),
                    intToBool(po.getLowerInclusive()), intToBool(po.getUpperInclusive()),
                    po.getRiskLevelCode(), po.getOrderNo() == null ? 0 : po.getOrderNo()));
        }
        return bands;
    }

    // —— 映射 ——

    private RulePackagePO toPO(RulePackage p) {
        RulePackagePO po = new RulePackagePO();
        po.setId(p.getId());
        po.setCode(p.getCode());
        po.setName(p.getName());
        po.setTriggerMode(p.getTriggerMode().name());
        po.setComputeMode(p.getComputeMode().name());
        po.setRiskTypeCode(p.getRiskTypeCode());
        po.setOwnerOrgId(p.getOwnerOrgId());
        po.setApplicableOrgId(p.getApplicableOrgId());
        po.setIncludeSubOrg(p.isIncludeSubOrg() ? 1 : 0);
        po.setStatus(p.getStatus().name());
        po.setWarnScoreEnabled(p.isWarnScoreEnabled() ? 1 : 0);
        po.setWarnScoreOp(p.getWarnScoreOp() == null ? null : p.getWarnScoreOp().name());
        po.setWarnScoreThreshold(p.getWarnScoreThreshold());
        po.setVersion(p.getVersion());
        return po;
    }

    private RulePackage toDomain(RulePackagePO po) {
        TriggerMode triggerMode = TriggerMode.valueOf(po.getTriggerMode());
        ComputeMode computeMode = ComputeMode.valueOf(po.getComputeMode());
        RulePackageStatus status = "ENABLED".equals(po.getStatus())
                ? RulePackageStatus.ENABLED : RulePackageStatus.DISABLED;
        WarnScoreOp warnScoreOp = po.getWarnScoreOp() == null ? null
                : WarnScoreOp.valueOf(po.getWarnScoreOp());
        return RulePackage.rehydrate(
                po.getId(), po.getCode(), po.getName(), triggerMode, computeMode,
                po.getRiskTypeCode(), po.getOwnerOrgId(), po.getApplicableOrgId(),
                intToBool(po.getIncludeSubOrg()), status,
                intToBool(po.getWarnScoreEnabled()), warnScoreOp, po.getWarnScoreThreshold(),
                po.getVersion() == null ? 1 : po.getVersion(),
                loadScenarioIds(po.getId()), loadEventCodes(po.getId()), loadScoreBands(po.getId()));
    }

    private static boolean intToBool(Integer v) {
        return v != null && v == 1;
    }
}
