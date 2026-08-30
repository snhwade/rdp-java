package com.riskplatform.ruleconfig.domain;

import com.riskplatform.common.error.ValidationException;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinition;
import com.riskplatform.ruleconfig.domain.indicator.SliceGranularity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** 指标定义校验单元测试（R7.1/R7.2/R7.3/R7.5）。 */
class IndicatorValidationTest {

    @Test
    void indicator_valid() {
        IndicatorDefinition d = IndicatorDefinition.create(
                null, "txn_cnt_7d", "交易次数", null, List.of("PAYMENT"),
                List.of("merchant"), 7, SliceGranularity.HOUR, "count + 1", "ZERO", "GENERAL_STATS", null);
        assertThat(d.getRefName()).isEqualTo("txn_cnt_7d");
        assertThat(d.getStatus()).isEqualTo(IndicatorDefinition.STATUS_OFFLINE);
    }

    @Test
    void indicator_refNameIllegalChar_rejected() {
        ValidationException ex = catchThrowableOfType(
                () -> IndicatorDefinition.create(null, "bad-name!", null, null, List.of("PAYMENT"),
                        List.of("m"), 7, SliceGranularity.HOUR, "x", null, null, null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("refName");
    }

    @Test
    void indicator_missingDimensions_rejected() {
        ValidationException ex = catchThrowableOfType(
                () -> IndicatorDefinition.create(null, "ok", null, null, List.of("PAYMENT"),
                        List.of(), 7, SliceGranularity.HOUR, "x", null, null, null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("dimensions");
    }

    @Test
    void indicator_missingEvents_rejected() {
        ValidationException ex = catchThrowableOfType(
                () -> IndicatorDefinition.create(null, "ok", null, null, List.of(),
                        List.of("m"), 7, SliceGranularity.HOUR, "x", null, null, null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("eventTypeCodes");
    }

    @Test
    void indicator_windowOutOfRange_rejected() {
        ValidationException ex = catchThrowableOfType(
                () -> IndicatorDefinition.create(null, "ok", null, null, List.of("PAYMENT"),
                        List.of("m"), 400, SliceGranularity.DAY, "x", null, null, null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("windowDays");
    }

    @Test
    void indicator_online_requiresEvents() {
        IndicatorDefinition d = IndicatorDefinition.rehydrate(
                1L, null, "ok", null, null, List.of(), List.of("m"), 7, SliceGranularity.HOUR, "x", null, "OFFLINE", null, null);
        ValidationException ex = catchThrowableOfType(d::online, ValidationException.class);
        assertThat(ex.getFields()).containsKey("eventTypeCodes");
    }
}
