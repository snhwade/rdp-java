package com.riskplatform.ruleconfig.application.decisionflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.application.decisionflow.DecisionFlowVersionAppService.VersionSummary;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlow;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowRepository;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowVersion;
import com.riskplatform.ruleconfig.domain.decisionmatrix.DecisionMatrixRepository;
import com.riskplatform.ruleconfig.domain.decisiontable.DecisionTableRepository;
import com.riskplatform.ruleconfig.domain.decisiontree.DecisionTreeRepository;
import com.riskplatform.ruleconfig.domain.eventtype.EventTypeRepository;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackageRepository;
import com.riskplatform.ruleconfig.domain.scorecard.ScorecardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 决策流版本上下线生命周期单元测试（R8.5/R8.6/R8.7 + V1）。
 */
class DecisionFlowVersionAppServiceTest {

    private static final Long FLOW_ID = 100L;

    private static final String VALID_SNAPSHOT = """
            {"name":"f","eventTypeCode":"EVT","startNodeId":"start","status":"ENABLED",
             "nodes":[
               {"nodeId":"start","type":"START","refType":null,"refId":null,"config":null},
               {"nodeId":"end","type":"END","refType":null,"refId":null,"config":"{\\"endDecision\\":\\"AUTO_PASS\\"}"}
             ],
             "edges":[{"from":"start","to":"end","condition":null,"trafficPercent":null,"isDefault":false}]
            }
            """;

    private InMemoryDecisionFlowVersionRepository repo;
    private DecisionFlowRepository flowRepository;
    private DecisionFlowVersionAppService service;
    private DecisionFlow flowEntity;

    @BeforeEach
    void setUp() {
        repo = new InMemoryDecisionFlowVersionRepository();
        flowRepository = mock(DecisionFlowRepository.class);
        flowEntity = DecisionFlow.create("f", "EVT",
                List.of(
                        new DecisionFlow.Node("start", DecisionFlow.NodeType.START, null, null, null),
                        new DecisionFlow.Node("end", DecisionFlow.NodeType.END, null, null,
                                "{\"endDecision\":\"AUTO_PASS\"}")),
                List.of(new DecisionFlow.Edge("start", "end", null, null, false)),
                "start");
        flowEntity.assignId(FLOW_ID);
        when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(flowEntity));
        when(flowRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        when(eventTypes.existsByCode("EVT")).thenReturn(true);

        service = new DecisionFlowVersionAppService(
                repo,
                flowRepository,
                eventTypes,
                mock(RulePackageRepository.class),
                mock(DecisionTableRepository.class),
                mock(DecisionTreeRepository.class),
                mock(DecisionMatrixRepository.class),
                mock(ScorecardRepository.class),
                new ObjectMapper(),
                () -> {
                    throw new IllegalStateException("no user");
                });
    }

    private void seedVersion(int version, String status) {
        DecisionFlowVersion v = new DecisionFlowVersion(FLOW_ID, version, VALID_SNAPSHOT, "tester");
        if (DecisionFlowVersion.STATUS_ONLINE.equals(status)) {
            v.online();
        }
        repo.save(v);
    }

    @Test
    void listVersions_returnsVersionNumberAndStatusDescending() {
        seedVersion(1, DecisionFlowVersion.STATUS_OFFLINE);
        seedVersion(2, DecisionFlowVersion.STATUS_ONLINE);

        List<VersionSummary> versions = service.listVersions(FLOW_ID);

        assertThat(versions).extracting(VersionSummary::version).containsExactly(2, 1);
        assertThat(versions.get(0).status()).isEqualTo("ONLINE");
        assertThat(versions.get(1).status()).isEqualTo("OFFLINE");
    }

    @Test
    void onlineVersion_setsTargetOnlineAndPreviousOnlineOffline() {
        seedVersion(1, DecisionFlowVersion.STATUS_ONLINE);
        seedVersion(2, DecisionFlowVersion.STATUS_OFFLINE);

        service.onlineVersion(FLOW_ID, 2);

        assertThat(repo.findByDecisionFlowIdAndVersion(FLOW_ID, 1).orElseThrow().getStatus())
                .isEqualTo("OFFLINE");
        assertThat(repo.findByDecisionFlowIdAndVersion(FLOW_ID, 2).orElseThrow().getStatus())
                .isEqualTo("ONLINE");
        assertThat(service.listVersions(FLOW_ID).stream().filter(v -> "ONLINE".equals(v.status())).count())
                .isEqualTo(1);
        assertThat(flowEntity.getPrevOnlineVersion()).isEqualTo(1);
    }

    @Test
    void onlineVersion_rejectsUnknownVersion() {
        seedVersion(1, DecisionFlowVersion.STATUS_OFFLINE);

        assertThatThrownBy(() -> service.onlineVersion(FLOW_ID, 99))
                .isInstanceOf(BizException.class);
    }

    @Test
    void offlineFlow_setsOnlineVersionOffline() {
        seedVersion(1, DecisionFlowVersion.STATUS_ONLINE);

        service.offlineFlow(FLOW_ID);

        assertThat(repo.findOnlineVersion(FLOW_ID)).isEmpty();
        assertThat(service.listVersions(FLOW_ID).stream().filter(v -> "ONLINE".equals(v.status())).count())
                .isZero();
    }

    @Test
    void offlineFlow_isNoOpWhenNoOnlineVersion() {
        seedVersion(1, DecisionFlowVersion.STATUS_OFFLINE);

        service.offlineFlow(FLOW_ID);

        assertThat(repo.findOnlineVersion(FLOW_ID)).isEmpty();
    }

    @Test
    void rollbackToPreviousOnline_rebringsPrevious() {
        seedVersion(1, DecisionFlowVersion.STATUS_ONLINE);
        seedVersion(2, DecisionFlowVersion.STATUS_OFFLINE);
        service.onlineVersion(FLOW_ID, 2);
        assertThat(flowEntity.getPrevOnlineVersion()).isEqualTo(1);

        service.rollbackToPreviousOnline(FLOW_ID);

        assertThat(repo.findByDecisionFlowIdAndVersion(FLOW_ID, 1).orElseThrow().getStatus())
                .isEqualTo("ONLINE");
        assertThat(repo.findByDecisionFlowIdAndVersion(FLOW_ID, 2).orElseThrow().getStatus())
                .isEqualTo("OFFLINE");
    }
}
