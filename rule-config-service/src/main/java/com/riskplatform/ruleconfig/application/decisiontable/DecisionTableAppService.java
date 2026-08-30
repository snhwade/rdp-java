package com.riskplatform.ruleconfig.application.decisiontable;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.decisiontable.DecisionTable;
import com.riskplatform.ruleconfig.domain.decisiontable.DecisionTableRepository;

import java.util.List;

/**
 * 决策表应用服务（S2）：CRUD + 配置变更广播。
 */
public class DecisionTableAppService {

    private final DecisionTableRepository repository;
    private final ConfigChangePublisher configChangePublisher;

    public DecisionTableAppService(DecisionTableRepository repository,
                                   ConfigChangePublisher configChangePublisher) {
        this.repository = repository;
        this.configChangePublisher = configChangePublisher;
    }

    public DecisionTable create(String name, String eventTypeCode, DecisionTable.HitPolicy hitPolicy,
                                List<DecisionTable.Column> columns, List<DecisionTable.Row> rows) {
        DecisionTable t = DecisionTable.create(name, eventTypeCode, hitPolicy, columns, rows);
        DecisionTable saved = repository.save(t);
        configChangePublisher.publishChange("DECISION_TABLE", String.valueOf(saved.getId()));
        return saved;
    }

    public DecisionTable update(Long id, String name, DecisionTable.HitPolicy hitPolicy,
                                List<DecisionTable.Column> columns, List<DecisionTable.Row> rows, String status) {
        DecisionTable t = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("决策表不存在: id=" + id));
        t.update(name, hitPolicy, columns, rows, status);
        DecisionTable saved = repository.update(t);
        configChangePublisher.publishChange("DECISION_TABLE", String.valueOf(id));
        return saved;
    }

    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw BizException.notFound("决策表不存在: id=" + id);
        }
        repository.deleteById(id);
        configChangePublisher.publishChange("DECISION_TABLE", String.valueOf(id));
    }

    public List<DecisionTable> list(String eventTypeCode) {
        return (eventTypeCode == null || eventTypeCode.isBlank())
                ? repository.findAll() : repository.findByEventTypeCode(eventTypeCode);
    }

    public DecisionTable get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("决策表不存在: id=" + id));
    }
}
