package com.riskplatform.ruleconfig.domain.dict;

import java.util.List;
import java.util.Optional;

/**
 * 决策标签仓储端口（R12.1）。基础设施层用 MyBatis-Plus 持久化到 decision_tag 表。
 */
public interface DecisionTagRepository {

    DecisionTag save(DecisionTag decisionTag);

    void update(DecisionTag decisionTag);

    Optional<DecisionTag> findById(Long id);

    Optional<DecisionTag> findByCode(String code);

    boolean existsByCode(String code);

    List<DecisionTag> findAll();

    void deleteById(Long id);
}
