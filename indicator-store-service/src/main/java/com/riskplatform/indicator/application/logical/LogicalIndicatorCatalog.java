package com.riskplatform.indicator.application.logical;

import java.util.List;
import java.util.Optional;

/** 逻辑指标定义快照来源（standalone 读 MySQL，remote 调 rule-config HTTP）。 */
public interface LogicalIndicatorCatalog {

    List<LogicalIndicatorDefinition> current();

    Optional<LogicalIndicatorDefinition> findOnline(String refName);

    void refresh();
}
