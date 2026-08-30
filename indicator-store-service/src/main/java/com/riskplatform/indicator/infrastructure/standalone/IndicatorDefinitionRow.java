package com.riskplatform.indicator.infrastructure.standalone;

record IndicatorDefinitionRow(String refName, String eventTypeCodes, String dimensions,
                              Integer windowDays, String sliceGranularity, String accScript) {
}
