package com.riskplatform.ruleconfig.domain.decisiontree;

import java.util.List;
import java.util.Optional;

/** 决策树仓储端口（S8）。 */
public interface DecisionTreeRepository {

    DecisionTree save(DecisionTree tree);

    DecisionTree update(DecisionTree tree);

    boolean deleteById(Long id);

    Optional<DecisionTree> findById(Long id);

    List<DecisionTree> findAll();

    List<DecisionTree> findByEventTypeCode(String eventTypeCode);
}
