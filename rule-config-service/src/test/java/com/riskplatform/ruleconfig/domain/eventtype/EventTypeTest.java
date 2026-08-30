package com.riskplatform.ruleconfig.domain.eventtype;

import com.riskplatform.common.error.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * EventType 领域模型与输入校验单元测试（R1.1/R1.3）。
 */
class EventTypeTest {

    @Test
    void create_validInput_defaultsToEnabled() {
        EventType et = EventType.create("B2B_RECV", "B2B 收款");
        assertThat(et.getStatus()).isEqualTo(EventTypeStatus.ENABLED);
        assertThat(et.isEnabled()).isTrue();
        assertThat(et.getCode()).isEqualTo("B2B_RECV");
    }

    @Test
    void create_blankName_rejected() {
        ValidationException ex = catchThrowableOfType(
                () -> EventType.create("CODE1", ""), ValidationException.class);
        assertThat(ex.getFields()).containsKey("name");
    }

    @Test
    void create_nameTooLong_rejected() {
        String longName = "x".repeat(EventType.NAME_MAX + 1);
        ValidationException ex = catchThrowableOfType(
                () -> EventType.create("CODE1", longName), ValidationException.class);
        assertThat(ex.getFields()).containsKey("name");
    }

    @Test
    void create_codeMaxBoundary_accepted() {
        String code = "a".repeat(EventType.CODE_MAX);
        EventType et = EventType.create(code, "name");
        assertThat(et.getCode()).hasSize(EventType.CODE_MAX);
    }

    @Test
    void create_codeTooLong_rejected() {
        String code = "a".repeat(EventType.CODE_MAX + 1);
        ValidationException ex = catchThrowableOfType(
                () -> EventType.create(code, "name"), ValidationException.class);
        assertThat(ex.getFields()).containsKey("code");
    }

    @Test
    void create_codeWithIllegalChar_rejected() {
        ValidationException ex = catchThrowableOfType(
                () -> EventType.create("BAD-CODE!", "name"), ValidationException.class);
        assertThat(ex.getFields()).containsKey("code");
    }

    @Test
    void disableAndEnable_togglesStatus() {
        EventType et = EventType.create("CODE1", "name");
        et.disable();
        assertThat(et.isEnabled()).isFalse();
        et.enable();
        assertThat(et.isEnabled()).isTrue();
    }

    @Test
    void create_blankCode_rejected() {
        assertThatThrownBy(() -> EventType.create("", "name"))
                .isInstanceOf(ValidationException.class);
    }

    // —— risk-console-redesign R2：场景/用途/分型扩展 ——

    @Test
    void createExtended_validInput_persistsAllAttributes() {
        EventType et = EventType.create("EVT1", "事件1", 5L,
                java.util.EnumSet.of(EventPurpose.COMPUTE, EventPurpose.DECISION), EventKind.FACT);
        assertThat(et.getScenarioId()).isEqualTo(5L);
        assertThat(et.getEventKind()).isEqualTo(EventKind.FACT);
        assertThat(et.getPurposes()).containsExactlyInAnyOrder(EventPurpose.COMPUTE, EventPurpose.DECISION);
    }

    @Test
    void createExtended_emptyPurposes_rejected() {
        ValidationException ex = catchThrowableOfType(
                () -> EventType.create("EVT1", "事件1", 5L,
                        java.util.EnumSet.noneOf(EventPurpose.class), EventKind.FACT),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("purposes");
    }

    @Test
    void createExtended_missingScenario_rejectedWithFieldName() {
        // scenarioId 缺失但提供了用途/分型 → 视为扩展创建，必填项缺失报字段名（R2.5）
        ValidationException ex = catchThrowableOfType(
                () -> EventType.create("EVT1", "事件1", null,
                        java.util.EnumSet.of(EventPurpose.COMPUTE), EventKind.DIMENSION),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("scenarioId");
    }

    @Test
    void edit_missingKind_rejectedWithFieldName() {
        EventType et = EventType.create("EVT1", "事件1", 5L,
                java.util.EnumSet.of(EventPurpose.COMPUTE), EventKind.FACT);
        ValidationException ex = catchThrowableOfType(
                () -> et.edit("新名", 5L, java.util.EnumSet.of(EventPurpose.COMPUTE), null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("eventKind");
    }

    @Test
    void edit_validInput_updatesAttributes() {
        EventType et = EventType.create("EVT1", "事件1", 5L,
                java.util.EnumSet.of(EventPurpose.COMPUTE), EventKind.FACT);
        et.edit("事件1改", 9L, java.util.EnumSet.of(EventPurpose.DECISION), EventKind.DIMENSION);
        assertThat(et.getName()).isEqualTo("事件1改");
        assertThat(et.getScenarioId()).isEqualTo(9L);
        assertThat(et.getEventKind()).isEqualTo(EventKind.DIMENSION);
        assertThat(et.getPurposes()).containsExactly(EventPurpose.DECISION);
    }
}
