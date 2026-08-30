package com.riskplatform.ruleconfig.domain.rulepackage;

import java.util.List;
import java.util.Optional;

/** 规则包启用快照仓储。 */
public interface RulePackageEnabledSnapshotRepository {

    RulePackageEnabledSnapshot save(RulePackageEnabledSnapshot snapshot);

    int findMaxVersion(Long rulePackageId);

    /** 按版本号降序。 */
    List<RulePackageEnabledSnapshot> findByRulePackageId(Long rulePackageId);

    Optional<RulePackageEnabledSnapshot> findByRulePackageIdAndVersion(Long rulePackageId, int version);
}
