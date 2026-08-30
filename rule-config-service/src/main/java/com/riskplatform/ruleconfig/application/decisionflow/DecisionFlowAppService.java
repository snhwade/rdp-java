package com.riskplatform.ruleconfig.application.decisionflow;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlow;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowRepository;

import java.util.List;

/**
 * 决策流应用服务（S4）：CRUD + 配置变更广播。
 *
 * <p>扩展阶段（R6.5）：在创建/更新成功后，调用 {@link DecisionFlowVersionAppService#snapshot}
 * 把当次决策流整体序列化为版本快照写入 {@code decision_flow_version}（版本号递增），供版本对比使用。
 */
public class DecisionFlowAppService {

    private final DecisionFlowRepository repository;
    private final ConfigChangePublisher configChangePublisher;
    private final DecisionFlowVersionAppService versionAppService;

    public DecisionFlowAppService(DecisionFlowRepository repository,
                                  ConfigChangePublisher configChangePublisher,
                                  DecisionFlowVersionAppService versionAppService) {
        this.repository = repository;
        this.configChangePublisher = configChangePublisher;
        this.versionAppService = versionAppService;
    }

    public DecisionFlow create(String name, String eventTypeCode, List<DecisionFlow.Node> nodes,
                               List<DecisionFlow.Edge> edges, String startNodeId) {
        return create(name, eventTypeCode, nodes, edges, startNodeId, null, null, null, false, null);
    }

    /**
     * 仅以名称 + 所属事件创建决策流（R8.2）。
     */
    public DecisionFlow create(String name, String eventTypeCode) {
        return create(name, eventTypeCode, null);
    }

    /** 创建决策流（可带备注 D1）。 */
    public DecisionFlow create(String name, String eventTypeCode, String remark) {
        String endConfig = "{\"" + DecisionFlow.END_DECISION_CONFIG_KEY + "\":\"MANUAL_REVIEW\"}";
        List<DecisionFlow.Node> nodes = List.of(
                new DecisionFlow.Node("start", DecisionFlow.NodeType.START, null, null, null),
                new DecisionFlow.Node("end", DecisionFlow.NodeType.END, null, null, endConfig));
        List<DecisionFlow.Edge> edges = List.of(
                new DecisionFlow.Edge("start", "end", null, null, false));
        return create(name, eventTypeCode, nodes, edges, "start", null, null, null, false, remark);
    }

    /** 创建决策流（含扩展阶段归属维度），保存期执行结构校验（唯一 START、至少一个 END）。 */
    public DecisionFlow create(String name, String eventTypeCode, List<DecisionFlow.Node> nodes,
                               List<DecisionFlow.Edge> edges, String startNodeId,
                               List<Long> scenarioIds, List<String> eventCodes,
                               Long applicableOrgId, boolean includeSubOrg) {
        return create(name, eventTypeCode, nodes, edges, startNodeId, scenarioIds, eventCodes,
                applicableOrgId, includeSubOrg, null);
    }

    public DecisionFlow create(String name, String eventTypeCode, List<DecisionFlow.Node> nodes,
                               List<DecisionFlow.Edge> edges, String startNodeId,
                               List<Long> scenarioIds, List<String> eventCodes,
                               Long applicableOrgId, boolean includeSubOrg, String remark) {
        DecisionFlow f = DecisionFlow.create(name, eventTypeCode, nodes, edges, startNodeId);
        f.assignScope(scenarioIds, eventCodes, applicableOrgId, includeSubOrg);
        f.assignRemark(remark);
        f.validateStructure();
        DecisionFlow saved = repository.save(f);
        versionAppService.snapshot(saved);
        configChangePublisher.publishChange("DECISION_FLOW", String.valueOf(saved.getId()));
        return saved;
    }

    public DecisionFlow update(Long id, String name, List<DecisionFlow.Node> nodes,
                               List<DecisionFlow.Edge> edges, String startNodeId, String status) {
        return update(id, name, nodes, edges, startNodeId, status, null, null, null, false, null);
    }

    /** 更新决策流（含扩展阶段归属维度），保存期执行结构校验。 */
    public DecisionFlow update(Long id, String name, List<DecisionFlow.Node> nodes,
                               List<DecisionFlow.Edge> edges, String startNodeId, String status,
                               List<Long> scenarioIds, List<String> eventCodes,
                               Long applicableOrgId, boolean includeSubOrg) {
        return update(id, name, nodes, edges, startNodeId, status, scenarioIds, eventCodes,
                applicableOrgId, includeSubOrg, null);
    }

    public DecisionFlow update(Long id, String name, List<DecisionFlow.Node> nodes,
                               List<DecisionFlow.Edge> edges, String startNodeId, String status,
                               List<Long> scenarioIds, List<String> eventCodes,
                               Long applicableOrgId, boolean includeSubOrg, String remark) {
        DecisionFlow f = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("决策流不存在: id=" + id));
        f.update(name, nodes, edges, startNodeId, status);
        f.assignScope(scenarioIds, eventCodes, applicableOrgId, includeSubOrg);
        if (remark != null) {
            f.assignRemark(remark);
        }
        f.validateStructure();
        DecisionFlow saved = repository.update(f);
        versionAppService.snapshot(saved);
        configChangePublisher.publishChange("DECISION_FLOW", String.valueOf(id));
        return saved;
    }

    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw BizException.notFound("决策流不存在: id=" + id);
        }
        repository.deleteById(id);
        configChangePublisher.publishChange("DECISION_FLOW", String.valueOf(id));
    }

    public List<DecisionFlow> list(String eventTypeCode) {
        return (eventTypeCode == null || eventTypeCode.isBlank())
                ? repository.findAll() : repository.findByEventTypeCode(eventTypeCode);
    }

    public DecisionFlow get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("决策流不存在: id=" + id));
    }
}
