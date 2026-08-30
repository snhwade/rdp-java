package com.riskplatform.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.gateway.integration.support.GatewayIntegrationTestDataMapper;
import com.riskplatform.gateway.integration.support.IntegrationTestJwt;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 查询监控集成测试（XL1 / XS1 / XT1）：引擎决策记录详情、时段统计与执行链路。
 */
class QueryMonitorIntegrationTest extends AbstractGatewayMySqlRedisIntegrationTest {

    private static final String MARKER = "evt-ZZIT-QRY-";
    private static final String EVENT_TYPE = "B2B_RECV";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private GatewayIntegrationTestDataMapper testData;

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
        seedEngineRecord(eventId, "PASS", 42L);
    }

    @AfterEach
    void tearDown() {
        cleanupMarkerData();
    }

    @Test
    void getEngineRecord_returnsSeededDetail() throws Exception {
        ResponseEntity<String> response = getJson("/api/v1/engine-decision-records/" + eventId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("eventId").asText()).isEqualTo(eventId);
        assertThat(body.get("engineDecision").asText()).isEqualTo("PASS");
        assertThat(body.get("finalDecision").asText()).isEqualTo("PASS");
        assertThat(body.get("elapsedMs").asLong()).isEqualTo(42L);
    }

    @Test
    void stats_includesSeededRecords() throws Exception {
        long now = System.currentTimeMillis();
        long start = now - 3_600_000L;

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl() + "/api/v1/engine-decision-records/stats")
                .queryParam("startTimeMs", start)
                .queryParam("endTimeMs", now)
                .queryParam("eventTypeCode", EVENT_TYPE)
                .toUriString();

        ResponseEntity<String> response = exchangeGet(url);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode stats = objectMapper.readTree(response.getBody());
        assertThat(stats.get("total").asLong()).isGreaterThanOrEqualTo(1L);
        assertThat(stats.get("decisionDistribution").get("PASS").asLong()).isGreaterThanOrEqualTo(1L);
        assertThat(stats.get("avgElapsedMs").asDouble()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void traceFromDecisionRecord_matchesEngineEventId() throws Exception {
        ResponseEntity<String> response = getJson("/api/v1/decision-records/" + eventId + "/trace");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode trace = objectMapper.readTree(response.getBody());
        assertThat(trace.get("eventId").asText()).isEqualTo(eventId);
        assertThat(trace.get("finalDecision").asText()).isEqualTo("PASS");
        assertThat(trace.get("ruleExecutions").isArray()).isTrue();
        assertThat(trace.get("ruleExecutions").size()).isGreaterThan(0);
    }

    private void seedEngineRecord(String evtId, String decision, long elapsedMs) {
        String correlationId = runId.replaceAll("[^a-fA-F0-9]", "a");
        if (correlationId.length() > 32) {
            correlationId = correlationId.substring(0, 32);
        } else if (correlationId.length() < 32) {
            correlationId = (correlationId + "0123456789abcdef0123456789abcdef").substring(0, 32);
        }
        String detailJson = """
                {
                  "decision": "%s",
                  "groupStatus": "COMPLETED",
                  "hits": [{"ruleId": 9001, "priority": 0, "decision": "%s", "trialRun": false}],
                  "records": [{"ruleId": 9001, "version": 1, "hit": true, "failed": false}]
                }
                """.formatted(decision, decision);
        testData.insertEngineDecisionRecord(
                evtId,
                correlationId,
                "M_IT_" + runId,
                EVENT_TYPE,
                decision,
                decision,
                detailJson,
                elapsedMs);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<String> getJson(String path) {
        return exchangeGet(baseUrl() + path);
    }

    private ResponseEntity<String> exchangeGet(String url) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    }

    private void cleanupMarkerData() {
        testData.deleteEngineDecisionRecordsByEventIdPattern(MARKER + "%");
    }
}
