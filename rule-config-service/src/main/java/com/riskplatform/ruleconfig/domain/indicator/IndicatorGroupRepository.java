package com.riskplatform.ruleconfig.domain.indicator;

import java.util.List;
import java.util.Optional;

public interface IndicatorGroupRepository {

    IndicatorGroup save(IndicatorGroup group);

    IndicatorGroup update(IndicatorGroup group);

    boolean deleteById(Long id);

    Optional<IndicatorGroup> findById(Long id);

    List<IndicatorGroup> findAll();

    boolean existsByName(String name);

    boolean existsByNameExceptId(String name, Long id);

    long countIndicators(Long groupId, String status);

    long countIndicatorsTotal(Long groupId);

    /** 未归属任何分组的指标数量。 */
    long countUngroupedIndicators(String status);
}
