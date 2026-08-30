package com.riskplatform.ruleconfig.domain.scorecard;

import java.util.List;
import java.util.Optional;

/** 评分卡仓储端口（S3）。 */
public interface ScorecardRepository {

    Scorecard save(Scorecard scorecard);

    Scorecard update(Scorecard scorecard);

    boolean deleteById(Long id);

    Optional<Scorecard> findById(Long id);

    List<Scorecard> findAll();

    List<Scorecard> findByEventTypeCode(String eventTypeCode);
}
