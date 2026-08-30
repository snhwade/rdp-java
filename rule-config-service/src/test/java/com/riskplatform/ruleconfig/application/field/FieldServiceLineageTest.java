package com.riskplatform.ruleconfig.application.field;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.error.RuleConfigErrorCode;
import com.riskplatform.ruleconfig.domain.field.FieldDefinition;
import com.riskplatform.ruleconfig.domain.field.FieldReferenceChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldServiceLineageTest {

    private InMemoryFieldRepository repo;
    private FieldService service;
    private boolean referenced;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFieldRepository();
        referenced = false;
        FieldReferenceChecker checker = (id, code) -> referenced ? List.of("事件字段", "规则包") : List.of();
        service = new FieldService(repo, checker);
    }

    @Test
    void deleteBlockedWhenReferenced() {
        FieldDefinition f = service.createField("amt", "金额", "Double", null);
        referenced = true;
        assertThatThrownBy(() -> service.deleteField(f.id()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException be = (BizException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(RuleConfigErrorCode.FIELD_IN_USE);
                    assertThat(be.getMessage()).contains("事件字段").contains("规则包");
                });
        assertThat(repo.findFieldById(f.id())).isPresent();
    }

    @Test
    void renameCodeBlockedWhenReferenced() {
        FieldDefinition f = service.createField("amt", "金额", "Double", null);
        referenced = true;
        assertThatThrownBy(() -> service.updateField(f.id(), "amount", "金额", "Double", null, true))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(RuleConfigErrorCode.FIELD_IN_USE));
        assertThat(repo.findFieldByCode("amt")).isPresent();
    }

    @Test
    void renameDisplayNameAllowedWhenReferenced() {
        FieldDefinition f = service.createField("amt", "金额", "Double", null);
        referenced = true;
        FieldDefinition updated = service.updateField(f.id(), "amt", "交易金额", "Double", null, true);
        assertThat(updated.name()).isEqualTo("交易金额");
    }

    @Test
    void deleteAllowedWhenNotReferenced() {
        FieldDefinition f = service.createField("tmp", "临时", "String", null);
        referenced = false;
        service.deleteField(f.id());
        assertThat(repo.findFieldById(f.id())).isEmpty();
    }
}
