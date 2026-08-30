package com.riskplatform.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.integration.support.EngineIntegrationTestDataMapper;
import com.riskplatform.engine.integration.support.IntegrationTestJwt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 执行链路查询集成测试（XT1）：{@code GET /api/v1/trace/{eventId}} 从 decision_log 还原链路。
 */
class TraceQueryIntegrationTest extends AbstractEngineMySqlRedisIntegrationTest {

    private static final String MARKER = "evt-ZZIT-TRC-";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EngineIntegrationTestDataMapper testData;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String runId;
    private String eventId;

    @BeforeEach
    void setUp() {
        token = IntegrationTestJwt.operatorToken();
        runId = Long.toString(System.nanoTime());
        eventId = MARKER + runId;
        cleanupMarkerData();
        seedDecisionLog(eventId);
    }

    @AfterEach
    void tearDown() {
        cleanupMarkerData();
    }

    @Test
    void trace_returnsSeededDecisionLogFromRealMySql() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/trace/" + eventId,
                HttpMethod.GET,
                entity,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("eventId").asText()).isEqualTo(eventId);
        assertThat(body.get("traceId").asText()).isEqualTo(eventId);
        assertThat(body.get("finalDecision").asText()).isEqualTo("PASS");
        assertThat(body.get("elapsedMs").asLong()).isEqualTo(55L);
        assertThat(body.get("groupStatus").asText()).isEqualTo("COMPLETED");
        assertThat(body.get("hitDecisions").isArray()).isTrue();
        assertThat(body.get("hitDecisions").size()).isEqualTo(1);
        assertThat(body.get("hitDecisions").get(0).get("ruleId").asLong()).isEqualTo(8001L);
    }

    private void seedDecisionLog(String evtId) {
        String hitRules = """
                [{"ruleId":8001,"priority":0,"decision":"PASS","trialRun":false}]
                """;
        testData.insertDecisionLog(evtId, "PASS", hitRules.trim(), 55, "COMPLETED");
    }

    private void cleanupMarkerData() {
        testData.deleteDecisionLogsByEventIdPattern(MARKER + "%");
    }
}
