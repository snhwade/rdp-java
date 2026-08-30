package com.riskplatform.ruleconfig.application.eventtype;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.application.audit.Audited;
import com.riskplatform.ruleconfig.domain.error.RuleConfigErrorCode;
import com.riskplatform.ruleconfig.domain.audit.AuditOpType;
import com.riskplatform.ruleconfig.domain.audit.AuditTargetType;
import com.riskplatform.ruleconfig.domain.eventtype.EventEngineStatusQuery;
import com.riskplatform.ruleconfig.domain.eventtype.EventKind;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;
import com.riskplatform.ruleconfig.domain.eventtype.EventReferenceChecker;
import com.riskplatform.ruleconfig.domain.eventtype.EventType;
import com.riskplatform.ruleconfig.domain.eventtype.EventTypeRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 事件类型应用服务（R1 / risk-console-redesign R2）。
 *
 * <p>负责事件的创建、编辑、删除、批量导入、列表/按场景查询及引擎状态查询的事务编排。
 * 领域不变式（name/code、用途非空子集、分型二选一、必填项）在 {@link EventType} 内完成。
 *
 * <p>校验责任划分：
 * <ul>
 *   <li>必填项缺失、用途空集、分型缺失 → {@link EventType#validate()} / {@link EventType#edit} 返回字段级错误（R2.5）</li>
 *   <li>code 范围内唯一 → 本服务通过仓储精确等值查询判定（R2.6，Property 8 思路：仅真实重复时拒绝）</li>
 *   <li>删除依赖拦截 → {@link EventReferenceChecker}（R2.9，本期默认无依赖，任务 2.3 接入真实实现）</li>
 *   <li>引擎可执行状态 → {@link EventEngineStatusQuery}（R2.11）</li>
 * </ul>
 */
public class EventTypeAppService {

    private final EventTypeRepository repository;
    private final EventReferenceChecker referenceChecker;
    private final EventEngineStatusQuery engineStatusQuery;

    public EventTypeAppService(EventTypeRepository repository,
                               EventReferenceChecker referenceChecker,
                               EventEngineStatusQuery engineStatusQuery) {
        this.repository = repository;
        this.referenceChecker = referenceChecker;
        this.engineStatusQuery = engineStatusQuery;
    }

    /** 创建事件（旧式仅 code/name，兼容既有调用方）。 */
    @Audited(target = AuditTargetType.EVENT_TYPE, op = AuditOpType.CREATE)
    public EventType create(String code, String name) {
        EventType eventType = EventType.create(code, name); // 校验 name/code（R1.1/R1.3）
        if (repository.existsByCode(code)) {
            throw BizException.duplicate("事件类型 code 已存在: " + code); // R1.2
        }
        return repository.save(eventType);
    }

    /**
     * 创建事件（risk-console-redesign R2.2）：含场景、用途多选、分型。
     *
     * <p>先经聚合校验必填项/用途非空子集/分型二选一（R2.3/R2.4/R2.5），
     * 再校验 code 范围内唯一（R2.6）。
     */
    @Audited(target = AuditTargetType.EVENT_TYPE, op = AuditOpType.CREATE)
    public EventType create(String code, String name, Long scenarioId,
                            Set<EventPurpose> purposes, EventKind eventKind) {
        EventType eventType = EventType.create(code, name, scenarioId, purposes, eventKind);
        if (repository.existsByCode(code)) {
            throw BizException.duplicate("事件 code 已存在: " + code); // R2.6
        }
        return repository.save(eventType);
    }

    /** 编辑事件名称、所属业务场景、事件用途与事件类型分型（R2.7）。 */
    @Audited(target = AuditTargetType.EVENT_TYPE, op = AuditOpType.UPDATE)
    public EventType edit(Long id, String name, Long scenarioId,
                          Set<EventPurpose> purposes, EventKind eventKind) {
        EventType eventType = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("事件不存在: " + id));
        eventType.edit(name, scenarioId, purposes, eventKind); // 校验必填项/用途/分型（R2.5）
        repository.update(eventType);
        return eventType;
    }

    /** 设置启用/禁用状态（R1.4）。 */
    @Audited(target = AuditTargetType.EVENT_TYPE, op = AuditOpType.UPDATE)
    public EventType setStatus(Long id, boolean enabled) {
        EventType eventType = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("事件类型不存在: " + id));
        if (enabled) {
            eventType.enable();
        } else {
            eventType.disable();
        }
        repository.update(eventType);
        return eventType;
    }

    /**
     * 删除事件（R2.8/R2.9）。
     *
     * <p>删除前由 {@link EventReferenceChecker} 检查关联依赖（事件字段/规则包/决策流/评级模型）；
     * 存在依赖则拒绝删除并返回 {@code EVENT.HAS_DEPENDENCY}（保留事件）。
     */
    @Audited(target = AuditTargetType.EVENT_TYPE, op = AuditOpType.DELETE)
    public void delete(Long id) {
        EventType eventType = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("事件不存在: " + id));
        List<String> dependencies = referenceChecker.findDependencies(eventType.getCode());
        if (dependencies != null && !dependencies.isEmpty()) {
            throw new BizException(RuleConfigErrorCode.EVENT_HAS_DEPENDENCY,
                    "事件存在关联依赖，无法删除: " + String.join("、", dependencies)); // R2.9
        }
        repository.deleteById(id);
    }

    /**
     * 批量导入事件（R2.10）：逐条校验，持久化全部校验通过的事件，
     * 为每条校验未通过的记录返回失败原因。整体在一个事务内执行。
     */
    @Transactional
    public ImportResult importEvents(List<ImportItem> items) {
        List<EventType> succeeded = new ArrayList<>();
        List<ImportFailure> failures = new ArrayList<>();
        List<ImportItem> safeItems = items == null ? List.of() : items;
        for (int i = 0; i < safeItems.size(); i++) {
            ImportItem item = safeItems.get(i);
            try {
                EventType created = create(item.code(), item.name(), item.scenarioId(),
                        item.purposes(), item.eventKind());
                succeeded.add(created);
            } catch (BizException ex) {
                failures.add(new ImportFailure(i, item.code(), reasonOf(ex)));
            }
        }
        return new ImportResult(succeeded, failures);
    }

    /** 列表查询（R1.6）。 */
    public List<EventType> list() {
        return repository.findAll();
    }

    /** 按所属业务场景查询事件（R2.1）。 */
    public List<EventType> listByScenario(Long scenarioId) {
        return repository.findByScenarioId(scenarioId);
    }

    /** 查询某事件在引擎中的可执行状态（R2.11）。 */
    public EventEngineStatusQuery.Status engineStatus(Long id) {
        EventType eventType = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("事件不存在: " + id));
        return engineStatusQuery.query(eventType.getCode());
    }

    // —— 内部辅助 ——

    private String reasonOf(BizException ex) {
        if (ex.getFields() != null && !ex.getFields().isEmpty()) {
            List<String> parts = new ArrayList<>();
            ex.getFields().forEach((field, reason) -> parts.add(field + ": " + reason));
            return String.join("; ", parts);
        }
        return ex.getMessage();
    }

    /** 导入记录。 */
    public record ImportItem(String code, String name, Long scenarioId,
                             Set<EventPurpose> purposes, EventKind eventKind) {
    }

    /** 单条导入失败信息（含行号、code 与原因）。 */
    public record ImportFailure(int index, String code, String reason) {
    }

    /** 批量导入汇总结果（成功列表 + 失败明细）。 */
    public record ImportResult(List<EventType> succeeded, List<ImportFailure> failures) {
    }
}
