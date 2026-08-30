package com.riskplatform.engine.domain.decisionflow.node;

import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.domain.list.ListCheckPort;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListCheckNodeHandlerTest {

    @Test
    void serviceBlackHit_rejectsWithoutContextFlag() {
        ListCheckPort port = ctx -> new ListCheckPort.ListHit(true, false, false, true);
        ListCheckNodeHandler handler = new ListCheckNodeHandler(port);
        FlowContext ctx = new FlowContext(emptyDef(), new HashMap<>(Map.of("merchantId", "M_BLACK")));

        NodeResult result = handler.handle(dummyNode(), ctx);

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).decision()).isEqualTo(Decision.REJECT);
        assertThat(ctx.env().get("blackHit")).isEqualTo(true);
        assertThat(result.assignments().get("listCheckFromService")).isEqualTo(true);
    }

    @Test
    void serviceWatchHit_reviews() {
        ListCheckPort port = ctx -> new ListCheckPort.ListHit(false, true, false, true);
        ListCheckNodeHandler handler = new ListCheckNodeHandler(port);
        FlowContext ctx = new FlowContext(emptyDef(), new HashMap<>());

        NodeResult result = handler.handle(dummyNode(), ctx);

        assertThat(result.hits().get(0).decision()).isEqualTo(Decision.REVIEW);
    }

    @Test
    void fallbackToContextInjection_whenServiceEmpty() {
        ListCheckPort port = ctx -> ListCheckPort.ListHit.empty();
        ListCheckNodeHandler handler = new ListCheckNodeHandler(port);
        FlowContext ctx = new FlowContext(emptyDef(), new HashMap<>(Map.of("blackHit", true)));

        NodeResult result = handler.handle(dummyNode(), ctx);

        assertThat(result.hits().get(0).decision()).isEqualTo(Decision.REJECT);
        assertThat(result.assignments().get("listCheckFromService")).isEqualTo(false);
    }

    private static DecisionFlowDef emptyDef() {
        return new DecisionFlowDef(List.of(), List.of(), "start", Map.of(), Map.of());
    }

    private static DecisionFlowDef.Node dummyNode() {
        return new DecisionFlowDef.Node(
                "list1",
                DecisionFlowDef.NodeType.LIST_CHECK,
                null, null, null);
    }
}
