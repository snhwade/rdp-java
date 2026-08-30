package com.riskplatform.ruleconfig.domain.decisionmatrix;

import java.util.List;
import java.util.Optional;

/** 决策矩阵仓储端口（S9）。 */
public interface DecisionMatrixRepository {

    DecisionMatrix save(DecisionMatrix matrix);

    DecisionMatrix update(DecisionMatrix matrix);

    boolean deleteById(Long id);

    Optional<DecisionMatrix> findById(Long id);

    List<DecisionMatrix> findAll();

    List<DecisionMatrix> findByEventTypeCode(String eventTypeCode);
}
