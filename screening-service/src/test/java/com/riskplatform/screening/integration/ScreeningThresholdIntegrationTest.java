package com.riskplatform.screening.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.screening.integration.support.IntegrationTestJwt;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 筛查阈值配置集成测试（R11.4）：运营可更新相似度阈值。
 */
class ScreeningThresholdIntegrationTest extends AbstractScreeningMySqlIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() {
        token = IntegrationTestJwt.operatorToken();
    }

    @Test
    void setThreshold_returnsUpdatedValue() throws Exception {
        HttpEntity<Map<String, Double>> entity = new HttpEntity<>(
                Map.of("value", 0.88),
                authHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/screening/threshold",
                HttpMethod.PUT,
                entity,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("value").asDouble()).isEqualTo(0.88);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
