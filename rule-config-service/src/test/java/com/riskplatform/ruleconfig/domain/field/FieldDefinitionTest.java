package com.riskplatform.ruleconfig.domain.field;

import com.riskplatform.common.error.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 字段库领域模型与输入校验单元测试（R3.2/R3.3/R3.5）。
 */
class FieldDefinitionTest {

    @Test
    void create_validInput_defaultsToEnabled() {
        FieldDefinition f = FieldDefinition.create("txn_amount", "交易金额", "Double", "本次交易金额");
        assertThat(f.enabled()).isTrue();
        assertThat(f.code()).isEqualTo("txn_amount");
        assertThat(f.name()).isEqualTo("交易金额");
        assertThat(f.dataType()).isEqualTo("Double");
    }

    @Test
    void create_blankCode_rejectedWithFieldName() {
        ValidationException ex = catchThrowableOfType(
                () -> FieldDefinition.create("", "名称", "String", null), ValidationException.class);
        assertThat(ex.getFields()).containsKey("code");
    }

    @Test
    void create_blankName_rejectedWithFieldName() {
        ValidationException ex = catchThrowableOfType(
                () -> FieldDefinition.create("code1", "", "String", null), ValidationException.class);
        assertThat(ex.getFields()).containsKey("name");
    }

    @Test
    void create_blankDataType_rejectedWithFieldName() {
        ValidationException ex = catchThrowableOfType(
                () -> FieldDefinition.create("code1", "名称", "", null), ValidationException.class);
        assertThat(ex.getFields()).containsKey("dataType");
    }

    @Test
    void create_missingAllRequired_returnsAllFieldNames() {
        ValidationException ex = catchThrowableOfType(
                () -> FieldDefinition.create(null, null, null, null), ValidationException.class);
        assertThat(ex.getFields()).containsKeys("code", "name", "dataType");
    }

    @Test
    void create_unsupportedDataType_rejected() {
        ValidationException ex = catchThrowableOfType(
                () -> FieldDefinition.create("code1", "名称", "Json", null), ValidationException.class);
        assertThat(ex.getFields()).containsKey("dataType");
    }

    @Test
    void create_supportedDataTypes_accepted() {
        for (String t : new String[] {"String", "Double", "Integer", "Boolean", "Date"}) {
            FieldDefinition f = FieldDefinition.create("c_" + t, "n_" + t, t, null);
            assertThat(f.dataType()).isEqualTo(t);
        }
    }

    @Test
    void create_codeWithIllegalChar_rejected() {
        ValidationException ex = catchThrowableOfType(
                () -> FieldDefinition.create("bad-code!", "名称", "String", null), ValidationException.class);
        assertThat(ex.getFields()).containsKey("code");
    }

    @Test
    void isSupportedDataType_caseInsensitive() {
        assertThat(FieldDefinition.isSupportedDataType("string")).isTrue();
        assertThat(FieldDefinition.isSupportedDataType("DOUBLE")).isTrue();
        assertThat(FieldDefinition.isSupportedDataType("json")).isFalse();
        assertThat(FieldDefinition.isSupportedDataType(null)).isFalse();
    }
}
