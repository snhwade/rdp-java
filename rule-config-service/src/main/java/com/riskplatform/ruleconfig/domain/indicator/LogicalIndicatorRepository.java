package com.riskplatform.ruleconfig.domain.indicator;

import java.util.List;
import java.util.Optional;

public interface LogicalIndicatorRepository {

    LogicalIndicator save(LogicalIndicator indicator, List<LogicalIndicatorMember> members);

    LogicalIndicator update(LogicalIndicator indicator, List<LogicalIndicatorMember> members);

    boolean deleteById(Long id);

    Optional<LogicalIndicator> findById(Long id);

    Optional<LogicalIndicator> findByRefName(String refName);

    List<LogicalIndicator> findAll(Long groupId, Boolean ungroupedOnly, String status);

    boolean existsByRefName(String refName);
}
