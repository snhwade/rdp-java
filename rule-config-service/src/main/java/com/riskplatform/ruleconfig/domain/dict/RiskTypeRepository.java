package com.riskplatform.ruleconfig.domain.dict;

import java.util.List;
import java.util.Optional;

/**
 * 风险类型仓储端口（R12.1）。基础设施层用 MyBatis-Plus 持久化到 risk_type 表。
 */
public interface RiskTypeRepository {

    RiskType save(RiskType riskType);

    void update(RiskType riskType);

    Optional<RiskType> findById(Long id);

    Optional<RiskType> findByCode(String code);

    boolean existsByCode(String code);

    List<RiskType> findAll();

    void deleteById(Long id);
}
