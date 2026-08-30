package com.riskplatform.ruleconfig.application.decisiontree;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.decisiontree.DecisionTree;
import com.riskplatform.ruleconfig.domain.decisiontree.DecisionTreeRepository;

import java.util.List;

/** 决策树应用服务（S8）：CRUD + 配置变更广播。 */
public class DecisionTreeAppService {

    private final DecisionTreeRepository repository;
    private final ConfigChangePublisher configChangePublisher;

    public DecisionTreeAppService(DecisionTreeRepository repository, ConfigChangePublisher configChangePublisher) {
        this.repository = repository;
        this.configChangePublisher = configChangePublisher;
    }

    public DecisionTree create(String name, String eventTypeCode, String rootNodeId,
                               List<DecisionTree.Node> nodes) {
        DecisionTree t = DecisionTree.create(name, eventTypeCode, rootNodeId, nodes);
        DecisionTree saved = repository.save(t);
        configChangePublisher.publishChange("DECISION_TREE", String.valueOf(saved.getId()));
        return saved;
    }

    public DecisionTree update(Long id, String name, String rootNodeId,
                               List<DecisionTree.Node> nodes, String status) {
        DecisionTree t = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("决策树不存在: id=" + id));
        t.update(name, rootNodeId, nodes, status);
        DecisionTree saved = repository.update(t);
        configChangePublisher.publishChange("DECISION_TREE", String.valueOf(id));
        return saved;
    }

    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw BizException.notFound("决策树不存在: id=" + id);
        }
        repository.deleteById(id);
        configChangePublisher.publishChange("DECISION_TREE", String.valueOf(id));
    }

    public List<DecisionTree> list(String eventTypeCode) {
        return (eventTypeCode == null || eventTypeCode.isBlank())
                ? repository.findAll() : repository.findByEventTypeCode(eventTypeCode);
    }

    public DecisionTree get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("决策树不存在: id=" + id));
    }
}
