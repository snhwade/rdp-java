package com.riskplatform.indicator.application.accumulate;

import java.util.List;

/** 指标定义快照来源（standalone 读 MySQL，remote 调 rule-config HTTP）。 */
public interface IndicatorDefinitionCatalog {

    List<IndicatorDefinition> current();

    void refresh();
}
