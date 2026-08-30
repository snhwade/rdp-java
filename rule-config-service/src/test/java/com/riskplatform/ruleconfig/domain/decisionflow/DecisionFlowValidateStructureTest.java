package com.riskplatform.ruleconfig.domain.decisionflow;

import com.riskplatform.common.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 决策流保存期结构校验单元测试（R9.5 可达路径终止性、R9.6 结束节点决策结果必填）。
 */
class DecisionFlowValidateStructureTest {

    private static DecisionFlow.Node start() {
        return new DecisionFlow.Node("start", DecisionFlow.NodeType.START, null, null, null);
    }

    private static DecisionFlow.Node end(String nodeId, String decision) {
        String config = decision == null ? null : "{\"endDecision\":\"" + decision + "\"}";
        return new DecisionFlow.Node(nodeId, DecisionFlow.NodeType.END, null, null, config);
    }

    private static DecisionFlow.Node rule(String nodeId) {
        return new DecisionFlow.Node(nodeId, DecisionFlow.NodeType.RULE_PACKAGE, "RULE_PACKAGE", 1L, null);
    }

    private static DecisionFlow.Edge edge(String from, String to) {
        return new DecisionFlow.Edge(from, to, null, null, false);
    }

    private static DecisionFlow flow(List<DecisionFlow.Node> nodes, List<DecisionFlow.Edge> edges) {
        return DecisionFlow.create("流程", "EVT", nodes, edges, "start");
    }

    @Test
    void validStructure_withTerminatingEndConfigured_passes() {
        DecisionFlow f = flow(
                List.of(start(), rule("r1"), end("end", "AUTO_PASS")),
                List.of(edge("start", "r1"), edge("r1", "end")));
        // 不抛异常即通过
        f.validateStructure();
    }

    @Test
    void danglingReachableNode_notTerminatingAtEnd_isRejected() {
        // r1 可达但无出线且非 END → 存在不以 END 终止的可达路径（R9.5）
        DecisionFlow f = flow(
                List.of(start(), rule("r1"), end("end", "AUTO_PASS")),
                List.of(edge("start", "r1"), edge("start", "end")));
        ValidationException ex = catchThrowableOfType(f::validateStructure, ValidationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getFields()).containsKey("edges");
        assertThat(ex.getFields().get("edges")).contains("未到达结束(END)节点");
    }

    @Test
    void endNode_missingDecision_isRejected() {
        DecisionFlow f = flow(
                List.of(start(), end("end", null)),
                List.of(edge("start", "end")));
        ValidationException ex = catchThrowableOfType(f::validateStructure, ValidationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getFields().get("nodes")).contains("必须配置决策结果");
    }

    @Test
    void endNode_illegalDecision_isRejected() {
        DecisionFlow f = flow(
                List.of(start(), end("end", "FOO")),
                List.of(edge("start", "end")));
        ValidationException ex = catchThrowableOfType(f::validateStructure, ValidationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getFields().get("nodes")).contains("决策结果非法");
    }

    @Test
    void allFourLegalDecisions_areAccepted() {
        for (String d : List.of("REFUND", "MANUAL_REVIEW", "AUTO_PASS", "AUTO_REJECT")) {
            DecisionFlow f = flow(
                    List.of(start(), end("end", d)),
                    List.of(edge("start", "end")));
            f.validateStructure();
        }
    }
}
