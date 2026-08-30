package com.riskplatform.ruleconfig.domain.strategy;

import com.riskplatform.common.error.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 验证策略领域模型与输入校验单元测试（risk-console-redesign / R5.4/R5.5/R5.6）。
 */
class StrategyDefVerifyTest {

    @Test
    void createVerify_validInput_defaultsToEnabled() {
        StrategyDef s = StrategyDef.createVerify("SMS_VERIFY", "短信核身", 100,
                StrategyScope.scenario(7L), null);
        assertThat(s.isEnabled()).isTrue();
        assertThat(s.getCategory()).isEqualTo(StrategyCategory.VERIFY);
        assertThat(s.getPriority()).isEqualTo(100);
        assertThat(s.getScope().isAnyScope()).isFalse();
        assertThat(s.getScope().getScenarioId()).isEqualTo(7L);
    }

    @Test
    void createVerify_anyScenario_accepted() {
        StrategyDef s = StrategyDef.createVerify("ANY_V", "通用核身", 1,
                StrategyScope.anyScenario(), null);
        assertThat(s.getScope().isAnyScope()).isTrue();
        assertThat(s.getScope().getScenarioId()).isNull();
    }

    @Test
    void createVerify_priorityBelowMin_rejected() {
        ValidationException ex = catchThrowableOfType(
                () -> StrategyDef.createVerify("V", "n", 0, StrategyScope.anyScenario(), null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("priority");
    }

    @Test
    void createVerify_priorityAboveMax_rejected() {
        ValidationException ex = catchThrowableOfType(
                () -> StrategyDef.createVerify("V", "n", 10000, StrategyScope.anyScenario(), null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("priority");
    }

    @Test
    void createVerify_priorityBoundaries_accepted() {
        assertThat(StrategyDef.createVerify("V1", "n", 1, StrategyScope.anyScenario(), null)
                .getPriority()).isEqualTo(1);
        assertThat(StrategyDef.createVerify("V2", "n", 9999, StrategyScope.anyScenario(), null)
                .getPriority()).isEqualTo(9999);
    }

    @Test
    void createVerify_nullScope_rejected() {
        // 既非具体场景也非不限场景 → scope 为 null（StrategyScope.scenario(null) 返回 null）。
        ValidationException ex = catchThrowableOfType(
                () -> StrategyDef.createVerify("V", "n", 100, StrategyScope.scenario(null), null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("scope");
    }

    @Test
    void createVerify_missingPriorityAndScope_returnsBothFieldNames() {
        ValidationException ex = catchThrowableOfType(
                () -> StrategyDef.createVerify("V", "n", null, null, null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKeys("priority", "scope");
    }

    @Test
    void updateVerify_appliesNewValuesAndValidates() {
        StrategyDef s = StrategyDef.createVerify("V", "n", 100, StrategyScope.anyScenario(), null);
        s.updateVerify("新名称", 5, StrategyScope.scenario(3L), "{}");
        assertThat(s.getName()).isEqualTo("新名称");
        assertThat(s.getPriority()).isEqualTo(5);
        assertThat(s.getScope().getScenarioId()).isEqualTo(3L);

        ValidationException ex = catchThrowableOfType(
                () -> s.updateVerify("n", 99999, StrategyScope.anyScenario(), null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("priority");
    }
}
