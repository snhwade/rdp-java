package com.riskplatform.ruleconfig.application.decisionmatrix;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.decisionmatrix.DecisionMatrix;
import com.riskplatform.ruleconfig.domain.decisionmatrix.DecisionMatrixRepository;

import java.util.List;

/** 决策矩阵应用服务（S9）：CRUD + 配置变更广播。 */
public class DecisionMatrixAppService {

    private final DecisionMatrixRepository repository;
    private final ConfigChangePublisher configChangePublisher;

    public DecisionMatrixAppService(DecisionMatrixRepository repository, ConfigChangePublisher configChangePublisher) {
        this.repository = repository;
        this.configChangePublisher = configChangePublisher;
    }

    public DecisionMatrix create(String name, String eventTypeCode, String rowVar, List<DecisionMatrix.Bin> rowBins,
                                 String colVar, List<DecisionMatrix.Bin> colBins, List<DecisionMatrix.Cell> cells) {
        DecisionMatrix m = DecisionMatrix.create(name, eventTypeCode, rowVar, rowBins, colVar, colBins, cells);
        DecisionMatrix saved = repository.save(m);
        configChangePublisher.publishChange("DECISION_MATRIX", String.valueOf(saved.getId()));
        return saved;
    }

    public DecisionMatrix update(Long id, String name, String rowVar, List<DecisionMatrix.Bin> rowBins,
                                 String colVar, List<DecisionMatrix.Bin> colBins, List<DecisionMatrix.Cell> cells,
                                 String status) {
        DecisionMatrix m = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("决策矩阵不存在: id=" + id));
        m.update(name, rowVar, rowBins, colVar, colBins, cells, status);
        DecisionMatrix saved = repository.update(m);
        configChangePublisher.publishChange("DECISION_MATRIX", String.valueOf(id));
        return saved;
    }

    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw BizException.notFound("决策矩阵不存在: id=" + id);
        }
        repository.deleteById(id);
        configChangePublisher.publishChange("DECISION_MATRIX", String.valueOf(id));
    }

    public List<DecisionMatrix> list(String eventTypeCode) {
        return (eventTypeCode == null || eventTypeCode.isBlank())
                ? repository.findAll() : repository.findByEventTypeCode(eventTypeCode);
    }

    public DecisionMatrix get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("决策矩阵不存在: id=" + id));
    }
}
