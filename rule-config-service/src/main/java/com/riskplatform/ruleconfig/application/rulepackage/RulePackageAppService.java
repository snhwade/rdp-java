package com.riskplatform.ruleconfig.application.rulepackage;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.application.rulev2.RuleV2AppService;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.rulepackage.ComputeMode;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackage;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackageRepository;
import com.riskplatform.ruleconfig.domain.rulepackage.ScoreBand;
import com.riskplatform.ruleconfig.domain.rulepackage.TriggerMode;
import com.riskplatform.ruleconfig.domain.rulepackage.WarnScoreOp;
import com.riskplatform.ruleconfig.domain.rulev2.RuleListItem;
import com.riskplatform.ruleconfig.domain.rulev2.RuleStatusCounts;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2Repository;
import com.riskplatform.ruleconfig.domain.rulev2.RuleV2Status;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则包应用服务（R1.1/R1.5/R1.6/R1.7）。
 *
 * <p>负责规则包的创建、更新基础信息、启停、分值区间维护、规则关联/解除、查询的编排。
 * 领域不变式（触发模式不可变、name/code 校验、分值区间不重叠）在 {@link RulePackage} 内完成；
 * 名称唯一性（R1.4）通过注入仓储查询的 {@link RulePackage.NameUniquenessChecker} 钩子校验。
 *
 * <p>启用/禁用、关联规则等配置变更后，通过 {@link ConfigChangePublisher} 广播配置变更，
 * 引擎订阅后在 5 秒内刷新本地缓存使其生效（R1.7）。
 *
 * <p>通过 {@code @Service} 组件扫描自注册，避免改动共享装配类 AppServiceConfig。
 */
@Service
public class RulePackageAppService {

    /** 配置变更类型标识（与引擎侧订阅约定一致）。 */
    private static final String CONFIG_TYPE = "RULE_PACKAGE";

    private final RulePackageRepository repository;
    private final RuleV2Repository ruleV2Repository;
    private final RuleV2AppService ruleV2AppService;
    private final ConfigChangePublisher configChangePublisher;
    private final RulePackageEnabledSnapshotAppService enabledSnapshotAppService;

    public RulePackageAppService(RulePackageRepository repository,
                                 RuleV2Repository ruleV2Repository,
                                 RuleV2AppService ruleV2AppService,
                                 ConfigChangePublisher configChangePublisher,
                                 @Lazy RulePackageEnabledSnapshotAppService enabledSnapshotAppService) {
        this.repository = repository;
        this.ruleV2Repository = ruleV2Repository;
        this.ruleV2AppService = ruleV2AppService;
        this.configChangePublisher = configChangePublisher;
        this.enabledSnapshotAppService = enabledSnapshotAppService;
    }

    /**
     * 创建规则包（R1.1/R1.4/R1.5/R1.6）。
     *
     * <p>触发模式创建后不可变；执行名称唯一性校验（同触发模式下）；评分模式可附带分值区间。
     */
    public RulePackage create(CreateCommand cmd) {
        RulePackage pkg = RulePackage.create(cmd.code(), cmd.name(), cmd.triggerMode(), cmd.computeMode(),
                cmd.riskTypeCode(), cmd.ownerOrgId(), cmd.applicableOrgId(), cmd.includeSubOrg());
        // R1.4 名称唯一性校验（注入仓储查询钩子）
        pkg.checkNameUnique(repository::existsByTriggerModeAndName);
        pkg.replaceScenarios(cmd.scenarioIds());
        pkg.replaceEvents(cmd.eventTypeCodes());
        if (cmd.triggerMode() == TriggerMode.SCORE && cmd.scoreBands() != null) {
            pkg.replaceScoreBands(cmd.scoreBands());
        }
        if (cmd.warnScoreEnabled()) {
            pkg.configureWarnScore(true, cmd.warnScoreOp(), cmd.warnScoreThreshold());
        }
        RulePackage saved = repository.save(pkg);
        configChangePublisher.publishChange(CONFIG_TYPE, String.valueOf(saved.getId()));
        return saved;
    }

    /**
     * 更新规则包基础信息（R1.5）。触发模式不可变更（不在更新范围内）。
     */
    public RulePackage update(Long id, UpdateCommand cmd) {
        RulePackage pkg = require(id);
        pkg.updateBasics(cmd.name(), cmd.computeMode(), cmd.riskTypeCode(),
                cmd.ownerOrgId(), cmd.applicableOrgId(), cmd.includeSubOrg());
        // R1.4 名称唯一性校验（排除自身）
        pkg.checkNameUnique(repository::existsByTriggerModeAndName);
        pkg.replaceScenarios(cmd.scenarioIds());
        pkg.replaceEvents(cmd.eventTypeCodes());
        pkg.configureWarnScore(cmd.warnScoreEnabled(), cmd.warnScoreOp(), cmd.warnScoreThreshold());
        pkg.bumpVersion();
        repository.update(pkg);
        configChangePublisher.publishChange(CONFIG_TYPE, String.valueOf(id));
        return pkg;
    }

    /**
     * 启用/禁用规则包（R1.7）。
     *
     * <p>状态变更后递增版本并广播配置变更，引擎 5 秒内生效。
     * 启用成功后写入启用快照（P2），供「回退到上一启用快照」使用。
     */
    public RulePackage setStatus(Long id, boolean enabled) {
        RulePackage pkg = require(id);
        if (enabled) {
            pkg.enable();
        } else {
            pkg.disable();
        }
        pkg.bumpVersion();
        repository.update(pkg);
        configChangePublisher.publishChange(CONFIG_TYPE, String.valueOf(id));
        if (enabled) {
            enabledSnapshotAppService.captureAfterEnable(id);
        }
        return pkg;
    }

    /** 回退到上一启用快照（P2）。 */
    public RulePackage rollbackToPreviousEnabled(Long id) {
        return enabledSnapshotAppService.rollbackToPreviousEnabled(id);
    }

    /** 启用快照列表（版本降序）。 */
    public List<RulePackageEnabledSnapshotAppService.SnapshotSummary> listEnabledSnapshots(Long id) {
        return enabledSnapshotAppService.listSnapshots(id);
    }

    /**
     * 全量替换评分模式分值区间（R1.6）。
     *
     * <p>仅评分模式允许；区间两两不重叠校验由聚合完成。
     */
    public RulePackage replaceScoreBands(Long id, List<ScoreBand> bands) {
        RulePackage pkg = require(id);
        pkg.replaceScoreBands(bands);
        pkg.bumpVersion();
        repository.update(pkg);
        configChangePublisher.publishChange(CONFIG_TYPE, String.valueOf(id));
        return pkg;
    }

    /**
     * 关联规则到规则包（R1.7：支持同一规则归属多个包，含包内优先级）。
     */
    public void associateRule(Long id, Long ruleV2Id, int priority) {
        require(id);
        repository.associateRule(id, ruleV2Id, priority);
        configChangePublisher.publishChange(CONFIG_TYPE, String.valueOf(id));
    }

    /** 解除规则与规则包的关联。 */
    public void dissociateRule(Long id, Long ruleV2Id) {
        require(id);
        repository.dissociateRule(id, ruleV2Id);
        configChangePublisher.publishChange(CONFIG_TYPE, String.valueOf(id));
    }

    /** 查询规则包关联的规则 id 列表（按包内优先级降序）。 */
    public List<Long> listRuleIds(Long id) {
        require(id);
        return repository.findRuleIds(id);
    }

    /** 按 id 查询（含场景/事件/分值区间）。 */
    public RulePackage get(Long id) {
        return require(id);
    }

    /** 列表查询。 */
    public List<RulePackage> list() {
        return repository.findAll();
    }

    /**
     * 按决策事件编码列出规则包并附带三态计数（R6.1/R6.6）。
     *
     * <p>{@code eventCode} 为空时返回全部规则包。三态计数由 {@link RuleV2Repository#countByStatusForPackages(List)}
     * 以单条 {@code GROUP BY rule_package_id, status} 查询批量聚合，避免逐包 N+1 查询。
     *
     * @param eventCode 决策事件编码（可空）
     * @return 规则包及其三态计数列表，保持仓储返回顺序
     */
    public List<RulePackageWithCounts> listByEventCodeWithCounts(String eventCode) {
        List<RulePackage> packages = repository.findByEventCode(eventCode);
        List<Long> ids = new ArrayList<>(packages.size());
        for (RulePackage p : packages) {
            ids.add(p.getId());
        }
        Map<Long, RuleStatusCounts> countsById = ruleV2Repository.countByStatusForPackages(ids);
        List<RulePackageWithCounts> result = new ArrayList<>(packages.size());
        for (RulePackage p : packages) {
            RuleStatusCounts counts = countsById.getOrDefault(p.getId(), RuleStatusCounts.empty());
            result.add(new RulePackageWithCounts(p, counts));
        }
        return result;
    }

    /**
     * 列出某规则包下的规则列表读模型（R6.4）：规则编码、名称、状态、决策事件、风险等级、风险分值。
     */
    public List<RuleListItem> listRules(Long rulePackageId) {
        require(rulePackageId);
        return ruleV2Repository.findListItemsByRulePackageId(rulePackageId);
    }

    /**
     * 对选中规则集合应用某批量操作，逐条返回处理结果（R6.5）。
     *
     * <p>逐条处理：单条失败不影响其它条目；每条选中规则在结果中都有对应项（成功/失败 + 原因）。
     * 支持的操作：删除、复制、移动、上线、试运行、下线、编辑机构、下载。上线/试运行/下线复用
     * {@link RuleV2AppService#changeStatus(Long, RuleV2Status)} 与 {@link RuleV2} 三态迁移语义。
     *
     * @param rulePackageId 规则包 id（校验存在）
     * @param command       批量操作命令（操作类型、规则 id 列表与可选参数）
     * @return 与选中规则一一对应的逐条结果列表
     */
    public List<BatchItemResult> batchOperate(Long rulePackageId, BatchCommand command) {
        require(rulePackageId);
        BatchOperation op = command.operation();
        List<Long> ruleIds = command.ruleIds() == null ? List.of() : command.ruleIds();
        List<BatchItemResult> results = new ArrayList<>(ruleIds.size());
        for (Long ruleId : ruleIds) {
            results.add(applyOne(op, ruleId, command));
        }
        // 批量操作后广播规则包配置变更，使引擎刷新缓存
        configChangePublisher.publishChange(CONFIG_TYPE, String.valueOf(rulePackageId));
        return results;
    }

    /** 对单条规则应用批量操作，捕获异常转为逐条失败结果（R6.5）。 */
    private BatchItemResult applyOne(BatchOperation op, Long ruleId, BatchCommand command) {
        if (ruleId == null) {
            return BatchItemResult.failure(null, "规则 id 为空");
        }
        try {
            return switch (op) {
                case DELETE -> {
                    int affected = ruleV2Repository.deleteById(ruleId);
                    yield affected > 0
                            ? BatchItemResult.success(ruleId, "已删除")
                            : BatchItemResult.failure(ruleId, "规则不存在");
                }
                case COPY -> {
                    RuleV2 copy = ruleV2Repository.copy(ruleId, command.targetRulePackageId(),
                            buildCopyCode(ruleId, command));
                    yield copy != null
                            ? BatchItemResult.successWithRef(ruleId, "已复制", copy.getId())
                            : BatchItemResult.failure(ruleId, "源规则不存在");
                }
                case MOVE -> {
                    if (command.targetRulePackageId() == null) {
                        yield BatchItemResult.failure(ruleId, "缺少目标规则包 id");
                    }
                    int affected = ruleV2Repository.moveToPackage(ruleId, command.targetRulePackageId());
                    yield affected > 0
                            ? BatchItemResult.success(ruleId, "已移动")
                            : BatchItemResult.failure(ruleId, "规则不存在");
                }
                case ONLINE -> changeStatus(ruleId, RuleV2Status.ONLINE);
                case TRIAL_RUN -> changeStatus(ruleId, RuleV2Status.TRIAL_RUN);
                case OFFLINE -> changeStatus(ruleId, RuleV2Status.OFFLINE);
                case EDIT_ORG -> {
                    int affected = ruleV2Repository.updateApplicableOrg(ruleId,
                            command.applicableOrgId(), command.includeSubOrg());
                    yield affected > 0
                            ? BatchItemResult.success(ruleId, "已更新机构")
                            : BatchItemResult.failure(ruleId, "规则不存在");
                }
                case DOWNLOAD -> {
                    RuleV2 rule = ruleV2Repository.findById(ruleId).orElse(null);
                    yield rule != null
                            ? BatchItemResult.successWithPayload(ruleId, "可下载", toDownloadPayload(rule))
                            : BatchItemResult.failure(ruleId, "规则不存在");
                }
            };
        } catch (RuntimeException e) {
            return BatchItemResult.failure(ruleId, e.getMessage());
        }
    }

    /** 切换规则三态并转为逐条结果（复用三态切换语义）。 */
    private BatchItemResult changeStatus(Long ruleId, RuleV2Status status) {
        RuleV2 updated = ruleV2AppService.changeStatus(ruleId, status);
        return BatchItemResult.success(ruleId, "状态已切换为 " + updated.getStatus().name());
    }

    /** 生成复制规则的新编码：优先使用命令携带的前缀，否则以源规则 id 派生。 */
    private static String buildCopyCode(Long ruleId, BatchCommand command) {
        if (command.copyCodePrefix() != null && !command.copyCodePrefix().isBlank()) {
            return command.copyCodePrefix() + "_" + ruleId;
        }
        return "COPY_" + ruleId + "_" + System.currentTimeMillis();
    }

    /** 构建规则下载的可序列化表示（R6.5：下载逐条返回规则的可序列化表示）。 */
    private static Map<String, Object> toDownloadPayload(RuleV2 rule) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", rule.getId());
        payload.put("code", rule.getCode());
        payload.put("name", rule.getName());
        payload.put("ruleKind", rule.getRuleKind() == null ? null : rule.getRuleKind().name());
        payload.put("status", rule.getStatus() == null ? null : rule.getStatus().name());
        payload.put("eventTypeCode", rule.getEventTypeCode());
        payload.put("riskLevelCode", rule.getRiskLevelCode());
        payload.put("riskTypeCode", rule.getRiskTypeCode());
        payload.put("baseScore", rule.getBaseScore());
        payload.put("priority", rule.getPriority());
        payload.put("condition", rule.getCondition());
        return payload;
    }

    private RulePackage require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("规则包不存在: " + id));
    }

    /**
     * 创建命令。触发模式/计算方式等已由适配层解析为领域枚举。
     */
    public record CreateCommand(String code, String name, TriggerMode triggerMode, ComputeMode computeMode,
                                String riskTypeCode, Long ownerOrgId, Long applicableOrgId, boolean includeSubOrg,
                                List<Long> scenarioIds, List<String> eventTypeCodes, List<ScoreBand> scoreBands,
                                boolean warnScoreEnabled, WarnScoreOp warnScoreOp, BigDecimal warnScoreThreshold) {
    }

    /**
     * 更新命令（不含触发模式，R1.1：创建后不可变）。
     */
    public record UpdateCommand(String name, ComputeMode computeMode, String riskTypeCode,
                                Long ownerOrgId, Long applicableOrgId, boolean includeSubOrg,
                                List<Long> scenarioIds, List<String> eventTypeCodes,
                                boolean warnScoreEnabled, WarnScoreOp warnScoreOp, BigDecimal warnScoreThreshold) {
    }

    /**
     * 规则包 + 三态计数读模型（R6.1/R6.6）。
     */
    public record RulePackageWithCounts(RulePackage rulePackage, RuleStatusCounts counts) {
    }

    /**
     * 批量操作类型（R6.5）：删除、复制、移动、上线、试运行、下线、编辑机构、下载。
     */
    public enum BatchOperation {
        DELETE, COPY, MOVE, ONLINE, TRIAL_RUN, OFFLINE, EDIT_ORG, DOWNLOAD
    }

    /**
     * 批量操作命令（R6.5）。
     *
     * @param operation           操作类型
     * @param ruleIds             选中规则 id 列表
     * @param targetRulePackageId 目标规则包 id（移动/复制用，可空）
     * @param copyCodePrefix      复制时新规则编码前缀（复制用，可空）
     * @param applicableOrgId     适用机构 id（编辑机构用，可空）
     * @param includeSubOrg       是否含下级机构（编辑机构用）
     */
    public record BatchCommand(BatchOperation operation, List<Long> ruleIds, Long targetRulePackageId,
                               String copyCodePrefix, Long applicableOrgId, boolean includeSubOrg) {
    }

    /**
     * 批量操作逐条结果（R6.5）：每条选中规则均有对应项。
     *
     * @param ruleId    规则 id
     * @param success   是否成功
     * @param message   结果说明 / 失败原因
     * @param refRuleId 关联规则 id（如复制产生的新规则 id，可空）
     * @param payload   附带数据（如下载的可序列化表示，可空）
     */
    public record BatchItemResult(Long ruleId, boolean success, String message,
                                  Long refRuleId, Object payload) {
        static BatchItemResult success(Long ruleId, String message) {
            return new BatchItemResult(ruleId, true, message, null, null);
        }

        static BatchItemResult successWithRef(Long ruleId, String message, Long refRuleId) {
            return new BatchItemResult(ruleId, true, message, refRuleId, null);
        }

        static BatchItemResult successWithPayload(Long ruleId, String message, Object payload) {
            return new BatchItemResult(ruleId, true, message, null, payload);
        }

        static BatchItemResult failure(Long ruleId, String reason) {
            return new BatchItemResult(ruleId, false, reason, null, null);
        }
    }
}
