package com.riskplatform.bff.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.bff.integration.support.BffIntegrationTestDataMapper;
import java.util.Map;
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
 * BFF 查询监控集成测试（XT1）：登录后经 BFF 代理查询执行链路。
 */
class BffTraceProxyIntegrationTest extends AbstractBffMySqlRedisIntegrationTest {

    private static final String MARKER = "evt-ZZIT-BFF-";
    private static final String EVENT_TYPE = "B2B_RECV";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BffIntegrationTestDataMapper testData;

    @Autowired
    private ObjectMapper objectMapper;

    private String eventId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        eventId = MARKER + System.nanoTime();
        testData.deleteEngineDecisionRecordsByEventIdPattern(MARKER + "%");
        seedEngineRecord(eventId);
        assertThat(testData.countEngineDecisionRecordByEventId(eventId)).isEqualTo(1);

        ResponseEntity<String> loginResp = restTemplate.postForEntity(
                baseUrl() + "/bff/api/v1/auth/login",
                Map.of("username", "admin", "password", "admin123"),
                String.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        token = objectMapper.readTree(loginResp.getBody()).get("token").asText();
    }

    @AfterEach
    void tearDown() {
        testData.deleteEngineDecisionRecordsByEventIdPattern(MARKER + "%");
    }

    @Test
    void traceViaBff_returnsSeededEngineRecord() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/bff/api/v1/trace/" + eventId,
                HttpMethod.GET,
                entity,
                String.class);

        assertThat(response.getStatusCode())
                .withFailMessage(() -> "trace failed: " + response.getBody())
                .isEqualTo(HttpStatus.OK);
        JsonNode trace = objectMapper.readTree(response.getBody());
        assertThat(trace.get("eventId").asText()).isEqualTo(eventId);
        assertThat(trace.get("finalDecision").asText()).isEqualTo("PASS");
        assertThat(trace.get("ruleExecutions").isArray()).isTrue();
        assertThat(trace.get("ruleExecutions").size()).isGreaterThan(0);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private void seedEngineRecord(String evtId) {
        String correlationId = Long.toHexString(System.nanoTime());
        while (correlationId.length() < 32) {
            correlationId = correlationId + "a";
        }
        correlationId = correlationId.substring(0, 32);
        String detailJson = """
                {
                  "decision": "PASS",
                  "groupStatus": "COMPLETED",
                  "hits": [{"ruleId": 7001, "priority": 0, "decision": "PASS", "trialRun": false}],
                  "records": [{"ruleId": 7001, "version": 1, "hit": true, "failed": false}]
                }
                """;
        testData.insertEngineDecisionRecord(
                evtId, correlationId, "M_BFF_IT", EVENT_TYPE, "PASS", "PASS", detailJson.trim(), 33L);
    }
}
