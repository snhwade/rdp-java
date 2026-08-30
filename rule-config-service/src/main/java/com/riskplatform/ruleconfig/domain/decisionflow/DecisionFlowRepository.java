package com.riskplatform.ruleconfig.domain.decisionflow;

import java.util.List;
import java.util.Optional;

/** 决策流仓储端口（S4）。 */
public interface DecisionFlowRepository {

    DecisionFlow save(DecisionFlow flow);

    DecisionFlow update(DecisionFlow flow);

    boolean deleteById(Long id);

    Optional<DecisionFlow> findById(Long id);

    List<DecisionFlow> findAll();

    List<DecisionFlow> findByEventTypeCode(String eventTypeCode);
}
