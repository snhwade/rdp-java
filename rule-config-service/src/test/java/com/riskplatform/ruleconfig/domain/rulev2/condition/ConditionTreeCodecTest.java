package com.riskplatform.ruleconfig.domain.rulev2.condition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionTreeCodecTest {

    @Test
    void fromJson_migratesLegacyCompactFormat() {
        ConditionNode root = ConditionTreeCodec.fromJson(
                "{\"op\":\"GT\",\"field\":\"txn_amount\",\"value\":50000}");

        assertThat(root.op()).isEqualTo(ConditionOp.LEAF);
        assertThat(root.left().ref()).isEqualTo("txn_amount");
        assertThat(root.left().dataType()).isEqualTo(DataType.NUMBER);
        assertThat(root.operator()).isEqualTo(Operator.GT);
        assertThat(root.right().value()).isEqualTo(50000);
    }

    @Test
    void fromJson_parsesStructuredTreeFromDb() {
        ConditionNode root = ConditionTreeCodec.fromJson("""
                {"op":"LEAF","left":{"source":"FIELD","ref":"txn_amount","dataType":"NUMBER"},"operator":"GT","right":{"kind":"CONST","value":50000}}
                """);
        assertThat(root.op()).isEqualTo(ConditionOp.LEAF);
        assertThat(root.right().kind()).isEqualTo(RightOperandKind.CONST);
        assertThat(root.right().value()).isEqualTo(50000);
    }
}
