package com.riskplatform.ruleconfig.domain.decisiontable;

import java.util.List;
import java.util.Optional;

/** 决策表仓储端口（S2）。 */
public interface DecisionTableRepository {

    DecisionTable save(DecisionTable table);

    DecisionTable update(DecisionTable table);

    boolean deleteById(Long id);

    Optional<DecisionTable> findById(Long id);

    List<DecisionTable> findAll();

    /** 按事件类型查启用的决策表（供引擎执行）。 */
    List<DecisionTable> findByEventTypeCode(String eventTypeCode);
}
