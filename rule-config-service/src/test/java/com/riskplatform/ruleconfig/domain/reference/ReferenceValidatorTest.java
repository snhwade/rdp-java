package com.riskplatform.ruleconfig.domain.reference;

import com.riskplatform.common.error.BizException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 跨模块引用校验领域服务单元测试（risk-console-redesign R14.1/R14.2，任务 2.3）。
 *
 * <p>验证规则/决策流/评级模型引用不存在的事件或事件字段时拒绝并返回 {@code REF.NOT_FOUND}
 * （Property 38），引用存在对象时通过。以内存假体 {@link ReferenceResolver} 驱动。
 */
class ReferenceValidatorTest {

    /** 内存版引用解析假体：事件以 code 集合、事件字段以 "event.field" 集合表示。 */
    private static final class InMemoryReferenceResolver implements ReferenceResolver {
        private final Set<String> events = new HashSet<>();
        private final Set<String> eventFields = new HashSet<>();

        void addEvent(String eventCode) {
            events.add(eventCode);
        }

        void addEventField(String eventCode, String fieldCode) {
            events.add(eventCode);
            eventFields.add(eventCode + "." + fieldCode);
        }

        @Override
        public boolean eventExists(String eventCode) {
            return eventCode != null && events.contains(eventCode);
        }

        @Override
        public boolean eventFieldExists(String eventCode, String fieldCode) {
            return eventFields.contains(eventCode + "." + fieldCode);
        }
    }

    private static final String REF_NOT_FOUND = "REF.NOT_FOUND";

    @Test
    void requireEvent_existing_passes() {
        InMemoryReferenceResolver resolver = new InMemoryReferenceResolver();
        resolver.addEvent("EVT1");
        ReferenceValidator validator = new ReferenceValidator(resolver);

        // 不抛异常即通过
        validator.requireEvent("EVT1");
        assertThat(validator.eventExists("EVT1")).isTrue();
    }

    @Test
    void requireEvent_missing_rejectedWithRefNotFound() {
        ReferenceValidator validator = new ReferenceValidator(new InMemoryReferenceResolver());

        BizException ex = catchThrowableOfType(
                () -> validator.requireEvent("GHOST"), BizException.class);
        assertThat(ex.getErrorCode().code()).isEqualTo(REF_NOT_FOUND);
    }

    @Test
    void requireEvent_blank_rejectedWithRefNotFound() {
        ReferenceValidator validator = new ReferenceValidator(new InMemoryReferenceResolver());
        assertThatThrownBy(() -> validator.requireEvent("  "))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> validator.requireEvent(null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void requireEventField_existing_passes() {
        InMemoryReferenceResolver resolver = new InMemoryReferenceResolver();
        resolver.addEventField("EVT1", "amount");
        ReferenceValidator validator = new ReferenceValidator(resolver);

        validator.requireEventField("EVT1", "amount");
        assertThat(validator.eventFieldExists("EVT1", "amount")).isTrue();
    }

    @Test
    void requireEventField_missingEvent_rejectedWithRefNotFound() {
        ReferenceValidator validator = new ReferenceValidator(new InMemoryReferenceResolver());

        BizException ex = catchThrowableOfType(
                () -> validator.requireEventField("GHOST", "amount"), BizException.class);
        assertThat(ex.getErrorCode().code()).isEqualTo(REF_NOT_FOUND);
        assertThat(ex.getMessage()).contains("事件不存在");
    }

    @Test
    void requireEventField_eventExistsButFieldMissing_rejectedWithRefNotFound() {
        InMemoryReferenceResolver resolver = new InMemoryReferenceResolver();
        resolver.addEvent("EVT1");
        ReferenceValidator validator = new ReferenceValidator(resolver);

        BizException ex = catchThrowableOfType(
                () -> validator.requireEventField("EVT1", "ghostField"), BizException.class);
        assertThat(ex.getErrorCode().code()).isEqualTo(REF_NOT_FOUND);
        assertThat(ex.getMessage()).contains("事件字段不存在");
    }

    @Test
    void eventFieldExists_nonExceptional_falseWhenMissing() {
        InMemoryReferenceResolver resolver = new InMemoryReferenceResolver();
        resolver.addEvent("EVT1");
        ReferenceValidator validator = new ReferenceValidator(resolver);

        assertThat(validator.eventFieldExists("EVT1", "ghost")).isFalse();
        assertThat(validator.eventFieldExists("GHOST", "amount")).isFalse();
    }
}
