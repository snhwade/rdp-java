package com.riskplatform.ruleconfig.infrastructure.rulev2;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.rulev2.DynamicScoreBand;
import com.riskplatform.ruleconfig.domain.rulev2.RuleKind;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2;
import com.riskplatform.ruleconfig.domain.rulev2.RuleListItem;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2Repository;
import com.riskplatform.ruleconfig.domain.rulev2.RuleStatusCounts;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2Status;
import com.riskplatform.ruleconfig.domain.rulev2.condition.ConditionNode;
import com.riskplatform.ruleconfig.domain.rulev2.condition.ConditionTreeCodec;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 结构化规则仓储 MyBatis-Plus 实现（R2/R4）。
 *
 * <p>聚合跨两张表持久化：
 * <ul>
 *   <li>{@code rule_v2}（主体，含 condition_json / compiled_expr / expr_version）</li>
 *   <li>{@code rule_dynamic_score}（评分规则动态分区间，全量替换）</li>
 * </ul>
 *
 * <p>条件树 {@link ConditionNode} 与 {@code condition_json} 列通过 {@link ConditionTreeCodec} 互转。
 * 动态分区间采用「全量替换」策略：更新时先删后插，保证与聚合内状态一致。
 */
@Repository
public class RuleV2RepositoryImpl implements RuleV2Repository {

    private final RuleV2Mapper ruleMapper;
    private final RuleDynamicScoreMapper dynamicScoreMapper;

    public RuleV2RepositoryImpl(RuleV2Mapper ruleMapper, RuleDynamicScoreMapper dynamicScoreMapper) {
        this.ruleMapper = ruleMapper;
        this.dynamicScoreMapper = dynamicScoreMapper;
    }

    @Override
    @Transactional
    public RuleV2 save(RuleV2 rule) {
        RuleV2PO po = toPO(rule);
        ruleMapper.insert(po);
        rule.assignId(po.getId());
        insertDynamicScores(po.getId(), rule.getDynamicScores());
        return rule;
    }

    @Override
    @Transactional
    public void update(RuleV2 rule) {
        ruleMapper.updateById(toPO(rule));
        Long id = rule.getId();
        dynamicScoreMapper.delete(new LambdaQueryWrapper<RuleDynamicScorePO>()
                .eq(RuleDynamicScorePO::getRuleV2Id, id));
        insertDynamicScores(id, rule.getDynamicScores());
    }

    @Override
    public Optional<RuleV2> findById(Long id) {
        return Optional.ofNullable(ruleMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<RuleV2> findAll() {
        return ruleMapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public List<RuleV2> findByRulePackageId(Long rulePackageId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<RuleV2PO>()
                        .eq(RuleV2PO::getRulePackageId, rulePackageId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<RuleListItem> findListItemsByRulePackageId(Long rulePackageId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<RuleV2PO>()
                        .eq(RuleV2PO::getRulePackageId, rulePackageId)
                        .orderByDesc(RuleV2PO::getPriority)
                        .orderByAsc(RuleV2PO::getId))
                .stream()
                .map(po -> new RuleListItem(po.getId(), po.getCode(), po.getName(), po.getStatus(),
                        po.getEventTypeCode(), po.getRiskLevelCode(), po.getBaseScore(), po.getRemark()))
                .toList();
    }

    @Override
    public RuleStatusCounts countByStatus(Long rulePackageId) {
        List<Map<String, Object>> rows = ruleMapper.countByStatusForPackage(rulePackageId);
        Map<String, Long> byStatus = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object statusObj = row.get("status");
            Object cntObj = row.get("cnt");
            String status = statusObj == null ? "" : statusObj.toString();
            long cnt = cntObj == null ? 0L : ((Number) cntObj).longValue();
            byStatus.merge(status, cnt, Long::sum);
        }
        return RuleStatusCounts.fromStatusCounts(byStatus);
    }

    @Override
    public Map<Long, RuleStatusCounts> countByStatusForPackages(List<Long> rulePackageIds) {
        if (rulePackageIds == null || rulePackageIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = ruleMapper.countByStatusForPackages(rulePackageIds);
        // 先按规则包 id 聚合「状态字符串 → 计数」，再归并为三态计数
        Map<Long, Map<String, Long>> byPackage = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object pidObj = row.get("rulePackageId");
            Object statusObj = row.get("status");
            Object cntObj = row.get("cnt");
            if (pidObj == null) {
                continue;
            }
            long pid = ((Number) pidObj).longValue();
            String status = statusObj == null ? "" : statusObj.toString();
            long cnt = cntObj == null ? 0L : ((Number) cntObj).longValue();
            byPackage.computeIfAbsent(pid, k -> new HashMap<>()).merge(status, cnt, Long::sum);
        }
        Map<Long, RuleStatusCounts> result = new HashMap<>();
        for (Map.Entry<Long, Map<String, Long>> e : byPackage.entrySet()) {
            result.put(e.getKey(), RuleStatusCounts.fromStatusCounts(e.getValue()));
        }
        return result;
    }

    @Override
    public int updateStatus(Long ruleId, String status) {
        return ruleMapper.updateStatus(ruleId, status);
    }

    @Override
    public int moveToPackage(Long ruleId, Long targetRulePackageId) {
        return ruleMapper.updateRulePackage(ruleId, targetRulePackageId);
    }

    @Override
    public int updateApplicableOrg(Long ruleId, Long applicableOrgId, boolean includeSubOrg) {
        return ruleMapper.updateApplicableOrg(ruleId, applicableOrgId, includeSubOrg ? 1 : 0);
    }

    @Override
    @Transactional
    public int deleteById(Long ruleId) {
        dynamicScoreMapper.delete(new LambdaQueryWrapper<RuleDynamicScorePO>()
                .eq(RuleDynamicScorePO::getRuleV2Id, ruleId));
        return ruleMapper.deleteById(ruleId);
    }

    @Override
    @Transactional
    public RuleV2 copy(Long sourceRuleId, Long targetRulePackageId, String newCode) {
        RuleV2PO source = ruleMapper.selectById(sourceRuleId);
        if (source == null) {
            return null;
        }
        RuleV2PO copy = new RuleV2PO();
        copy.setCode(newCode);
        copy.setName(source.getName());
        copy.setRulePackageId(targetRulePackageId != null ? targetRulePackageId : source.getRulePackageId());
        copy.setRuleKind(source.getRuleKind());
        copy.setEventTypeCode(source.getEventTypeCode());
        copy.setRiskLevelCode(source.getRiskLevelCode());
        copy.setRiskTypeCode(source.getRiskTypeCode());
        copy.setBaseScore(source.getBaseScore());
        copy.setConditionJson(source.getConditionJson());
        copy.setCompiledExpr(source.getCompiledExpr());
        copy.setExprVersion(source.getExprVersion() == null ? 0 : source.getExprVersion());
        copy.setPriority(source.getPriority());
        copy.setShortCircuited(source.getShortCircuited());
        copy.setApplicableOrgId(source.getApplicableOrgId());
        copy.setIncludeSubOrg(source.getIncludeSubOrg());
        copy.setRemark(source.getRemark());
        copy.setVersion(1);
        // 复制出的规则默认下线（OFFLINE），需人工确认后再上线
        copy.setStatus(RuleStatusCounts.OFFLINE);
        ruleMapper.insert(copy);
        // 复制动态分区间
        List<RuleDynamicScorePO> bands = dynamicScoreMapper.selectList(
                new LambdaQueryWrapper<RuleDynamicScorePO>()
                        .eq(RuleDynamicScorePO::getRuleV2Id, sourceRuleId));
        for (RuleDynamicScorePO b : bands) {
            RuleDynamicScorePO nb = new RuleDynamicScorePO();
            nb.setRuleV2Id(copy.getId());
            nb.setIndicatorRefName(b.getIndicatorRefName());
            nb.setLower(b.getLower());
            nb.setUpper(b.getUpper());
            nb.setLowerInclusive(b.getLowerInclusive());
            nb.setUpperInclusive(b.getUpperInclusive());
            nb.setScore(b.getScore());
            nb.setOrderNo(b.getOrderNo());
            dynamicScoreMapper.insert(nb);
        }
        return findById(copy.getId()).orElse(null);
    }

    @Override
    public boolean existsByCompiledExpr(String compiledExpr, Long selfId) {
        if (compiledExpr == null || compiledExpr.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<RuleV2PO> wrapper = new LambdaQueryWrapper<RuleV2PO>()
                .eq(RuleV2PO::getCompiledExpr, compiledExpr);
        if (selfId != null) {
            wrapper.ne(RuleV2PO::getId, selfId);
        }
        return ruleMapper.exists(wrapper);
    }

    // —— 内部辅助：动态分子表写入 ——

    private void insertDynamicScores(Long ruleId, List<DynamicScoreBand> bands) {
        for (DynamicScoreBand band : bands) {
            RuleDynamicScorePO po = new RuleDynamicScorePO();
            po.setRuleV2Id(ruleId);
            po.setIndicatorRefName(band.indicatorRefName());
            po.setLower(band.lower());
            po.setUpper(band.upper());
            po.setLowerInclusive(band.lowerInclusive() ? 1 : 0);
            po.setUpperInclusive(band.upperInclusive() ? 1 : 0);
            po.setScore(band.score());
            po.setOrderNo(band.orderNo());
            dynamicScoreMapper.insert(po);
        }
    }

    private List<DynamicScoreBand> loadDynamicScores(Long ruleId) {
        List<RuleDynamicScorePO> pos = dynamicScoreMapper.selectList(
                new LambdaQueryWrapper<RuleDynamicScorePO>()
                        .eq(RuleDynamicScorePO::getRuleV2Id, ruleId)
                        .orderByAsc(RuleDynamicScorePO::getOrderNo));
        List<DynamicScoreBand> bands = new ArrayList<>(pos.size());
        for (RuleDynamicScorePO po : pos) {
            bands.add(DynamicScoreBand.of(po.getIndicatorRefName(), po.getLower(), po.getUpper(),
                    intToBool(po.getLowerInclusive()), intToBool(po.getUpperInclusive()),
                    po.getScore(), po.getOrderNo() == null ? 0 : po.getOrderNo()));
        }
        return bands;
    }

    // —— 映射 ——

    private RuleV2PO toPO(RuleV2 r) {
        RuleV2PO po = new RuleV2PO();
        po.setId(r.getId());
        po.setCode(r.getCode());
        po.setName(r.getName());
        po.setRulePackageId(r.getRulePackageId());
        po.setRuleKind(r.getRuleKind().name());
        po.setEventTypeCode(r.getEventTypeCode());
        po.setRiskLevelCode(r.getRiskLevelCode());
        po.setRiskTypeCode(r.getRiskTypeCode());
        po.setBaseScore(r.getBaseScore());
        po.setConditionJson(r.getCondition() == null ? null : ConditionTreeCodec.toJson(r.getCondition()));
        po.setCompiledExpr(r.getCompiledExpr());
        po.setExprVersion(r.getExprVersion());
        po.setPriority(r.getPriority());
        po.setShortCircuited(r.isShortCircuited() ? 1 : 0);
        po.setApplicableOrgId(r.getApplicableOrgId());
        po.setIncludeSubOrg(r.isIncludeSubOrg() ? 1 : 0);
        po.setRemark(r.getRemark());
        po.setVersion(r.getVersion());
        po.setStatus(r.getStatus().name());
        return po;
    }

    private RuleV2 toDomain(RuleV2PO po) {
        ConditionNode condition = (po.getConditionJson() == null || po.getConditionJson().isBlank())
                ? null : ConditionTreeCodec.fromJson(po.getConditionJson());
        RuleKind ruleKind = RuleKind.valueOf(po.getRuleKind());
        RuleV2Status status = parseStatus(po.getStatus());
        return RuleV2.rehydrate(
                po.getId(), po.getCode(), po.getName(), po.getRulePackageId(), ruleKind,
                po.getEventTypeCode(), po.getRiskLevelCode(), po.getRiskTypeCode(), po.getBaseScore(),
                condition, po.getCompiledExpr(), po.getExprVersion() == null ? 0 : po.getExprVersion(),
                po.getPriority() == null ? 0 : po.getPriority(), intToBool(po.getShortCircuited()),
                po.getApplicableOrgId(), intToBool(po.getIncludeSubOrg()), po.getRemark(),
                po.getVersion() == null ? 1 : po.getVersion(), status, loadDynamicScores(po.getId()));
    }

    private static boolean intToBool(Integer v) {
        return v != null && v == 1;
    }

    /**
     * 解析 {@code rule_v2.status} 列为三态枚举（R7.1/R7.8）。
     *
     * <p>兼容历史数据：未经 V22 迁移的旧值 {@code ENABLED→ONLINE}、{@code DISABLED→OFFLINE}；
     * 未知或空值按下线（{@code OFFLINE}）处理。
     */
    private static RuleV2Status parseStatus(String status) {
        if (status == null) {
            return RuleV2Status.OFFLINE;
        }
        return switch (status) {
            case "ONLINE", "ENABLED" -> RuleV2Status.ONLINE;
            case "TRIAL_RUN" -> RuleV2Status.TRIAL_RUN;
            default -> RuleV2Status.OFFLINE;
        };
    }
}
