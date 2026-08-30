package com.riskplatform.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.gateway.infrastructure.standalone.StandaloneListRecordMapper;
import com.riskplatform.gateway.infrastructure.standalone.StandaloneListRecordPO;
import com.riskplatform.gateway.integration.support.GatewayIntegrationTestDataMapper;
import com.riskplatform.gateway.integration.support.IntegrationTestJwt;
import java.util.HashMap;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 订单查询集成测试（R10/T06）：受理风控事件后 {@code GET /api/v1/orders} 可查到订单。
 */
class OrderQueryIntegrationTest extends AbstractGatewayMySqlRedisIntegrationTest {

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
    private String merchantId;
    private String eventId;

    @BeforeEach
    void setUp() {
        token = IntegrationTestJwt.operatorToken();
        merchantId = "ZZIT_ORD_" + System.nanoTime();
    }

    @AfterEach
    void tearDown() {
        if (eventId != null) {
            testData.deleteEngineDecisionRecordByEventId(eventId);
            testData.deleteRiskOrderByEventId(eventId);
            eventId = null;
        }
    }

    @Test
    void queryOrders_afterRiskEvent_returnsPersistedOrder() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("eventTypeCode", EVENT_TYPE);
        body.put("context", Map.of("merchantId", merchantId, "amount", 5000));

        ResponseEntity<String> accepted = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/risk-events", body, String.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode result = objectMapper.readTree(accepted.getBody());
        eventId = result.get("eventId").asText();
        assertThat(testData.countRiskOrderByEventId(eventId)).isEqualTo(1);

        String url = UriComponentsBuilder.fromHttpUrl("http://localhost:" + port + "/api/v1/orders")
                .queryParam("merchantId", merchantId)
                .queryParam("pageSize", 10)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> orders = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(orders.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = objectMapper.readTree(orders.getBody());
        assertThat(page.get("total").asLong()).isGreaterThanOrEqualTo(1L);
        assertThat(page.get("data").isArray()).isTrue();
        assertThat(page.get("data").findValuesAsText("eventId")).contains(eventId);
    }
}
