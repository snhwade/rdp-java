package com.riskplatform.ruleconfig.application.field;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.common.error.ValidationException;
import com.riskplatform.ruleconfig.domain.field.DerivedField;
import com.riskplatform.ruleconfig.domain.field.FieldDefinition;
import com.riskplatform.ruleconfig.domain.field.FieldImportResult;
import com.riskplatform.ruleconfig.domain.field.FieldRelations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 字段库应用服务单元测试（R3.2/R3.4/R3.5/R3.6/R3.7）。
 */
class FieldServiceTest {

    private InMemoryFieldRepository repo;
    private FieldService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFieldRepository();
        service = new FieldService(repo);
    }

    @Test
    void createField_persistsAndReturnsId() {
        FieldDefinition f = service.createField("txn_amount", "交易金额", "Double", "金额");
        assertThat(f.id()).isNotNull();
        assertThat(service.listFields()).hasSize(1);
    }

    @Test
    void createField_duplicateCode_rejected() {
        service.createField("txn_amount", "交易金额", "Double", null);
        BizException ex = catchThrowableOfType(
                () -> service.createField("txn_amount", "另一个", "String", null), BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.DUPLICATE);
    }

    @Test
    void createField_prefixSimilarCode_notRejected() {
        // R3.4：互为前缀的相似 code 不应误判为重复。
        service.createField("amount", "金额", "Double", null);
        FieldDefinition similar = service.createField("amount_total", "总金额", "Double", null);
        assertThat(similar.id()).isNotNull();
        assertThat(service.listFields()).hasSize(2);
    }

    @Test
    void createField_missingRequired_returnsFieldNames() {
        ValidationException ex = catchThrowableOfType(
                () -> service.createField(null, null, null, null), ValidationException.class);
        assertThat(ex.getFields()).containsKeys("code", "name", "dataType");
    }

    @Test
    void updateField_sameCode_notRejected() {
        FieldDefinition f = service.createField("txn_amount", "交易金额", "Double", null);
        FieldDefinition updated = service.updateField(f.id(), "txn_amount", "交易金额改", "Integer", "改", true);
        assertThat(updated.name()).isEqualTo("交易金额改");
        assertThat(updated.dataType()).isEqualTo("Integer");
    }

    @Test
    void updateField_changeToExistingCode_rejected() {
        service.createField("a_code", "A", "String", null);
        FieldDefinition b = service.createField("b_code", "B", "String", null);
        assertThatThrownBy(() -> service.updateField(b.id(), "a_code", "B", "String", null, true))
                .isInstanceOf(BizException.class);
    }

    @Test
    void updateField_nonExistent_rejected() {
        assertThatThrownBy(() -> service.updateField(999L, "x", "n", "String", null, true))
                .isInstanceOf(BizException.class);
    }

    @Test
    void importFields_mixedValidInvalid_persistsValidAndReportsFailures() {
        List<FieldService.FieldImportRecord> records = List.of(
                new FieldService.FieldImportRecord("good1", "好字段1", "String", null),
                new FieldService.FieldImportRecord("", "缺code", "String", null),       // 无效：缺 code
                new FieldService.FieldImportRecord("good2", "好字段2", "Double", null),
                new FieldService.FieldImportRecord("bad_type", "坏类型", "Json", null),  // 无效：类型不支持
                new FieldService.FieldImportRecord("good1", "重复code", "String", null) // 无效：批内重复
        );
        FieldImportResult result = service.importFields(records);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(3);
        assertThat(result.failures()).allSatisfy(f -> assertThat(f.reason()).isNotBlank());
        assertThat(service.listFields()).hasSize(2);
    }

    @Test
    void relations_returnsReferencingDerivedAndEvents() {
        FieldDefinition f = service.createField("txn_amount", "交易金额", "Double", null);
        // 衍生字段表达式引用该字段 code。
        repo.saveDerived(DerivedField.create("PAY_EVENT", "double_amount", "txn_amount * 2"));
        repo.saveDerived(DerivedField.create("OTHER_EVENT", "unrelated", "foo + bar"));

        FieldRelations relations = service.relations(f.id());
        assertThat(relations.derivedFields()).hasSize(1);
        assertThat(relations.derivedFields().get(0).name()).isEqualTo("double_amount");
        assertThat(relations.events()).containsExactly("PAY_EVENT");
        assertThat(relations.enumValues()).isEmpty();
    }

    @Test
    void relations_nonExistentField_rejected() {
        assertThatThrownBy(() -> service.relations(999L)).isInstanceOf(BizException.class);
    }
}
