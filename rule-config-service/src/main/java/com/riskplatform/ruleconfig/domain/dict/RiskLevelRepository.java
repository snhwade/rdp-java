package com.riskplatform.ruleconfig.domain.dict;

import java.util.List;
import java.util.Optional;

/**
 * 风险等级仓储端口（R12.1）。基础设施层用 MyBatis-Plus 持久化到 risk_level 表。
 */
public interface RiskLevelRepository {

    RiskLevel save(RiskLevel riskLevel);

    void update(RiskLevel riskLevel);

    Optional<RiskLevel> findById(Long id);

    Optional<RiskLevel> findByCode(String code);

    boolean existsByCode(String code);

    List<RiskLevel> findAll();

    void deleteById(Long id);
}
