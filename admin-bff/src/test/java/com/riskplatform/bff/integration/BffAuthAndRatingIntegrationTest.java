package com.riskplatform.bff.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.bff.integration.support.BffIntegrationTestDataMapper;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * BFF 集成测试：登录鉴权与商户评级代理（standalone 嵌入后端）。
 */
class BffAuthAndRatingIntegrationTest extends AbstractBffMySqlRedisIntegrationTest {

    private static final String MARKER = "ZZIT_BFF_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BffIntegrationTestDataMapper testData;

    @Autowired
    private ObjectMapper objectMapper;

    private String merchantId;

    @BeforeEach
    void setUp() {
        merchantId = MARKER + System.nanoTime();
        cleanupMarkerData();
    }

    @AfterEach
    void tearDown() {
        cleanupMarkerData();
    }

    @Test
    void login_andMerchantRatingProxy_roundTripThroughEmbeddedBackend() throws Exception {
        ResponseEntity<String> loginResp = restTemplate.postForEntity(
                baseUrl() + "/bff/api/v1/auth/login",
                Map.of("username", "admin", "password", "admin123"),
                String.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode login = objectMapper.readTree(loginResp.getBody());
        assertThat(login.get("token").asText()).isNotBlank();
        String token = login.get("token").asText();

        HttpHeaders headers = authHeaders(token);

        ResponseEntity<String> unrated = exchangeGet("/bff/api/v1/merchants/" + merchantId + "/rating", headers);
        assertThat(unrated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(unrated.getBody()).get("status").asText()).isEqualTo("UNRATED");

        Map<String, Object> factors = new HashMap<>();
        factors.put("industry", 1.0);
        factors.put("region", 0.5);
        factors.put("history", 0.8);
        HttpEntity<Map<String, Object>> postEntity = new HttpEntity<>(Map.of("factors", factors), headers);
        ResponseEntity<String> rated = restTemplate.exchange(
                baseUrl() + "/bff/api/v1/merchants/" + merchantId + "/rating",
                HttpMethod.POST,
                postEntity,
                String.class);
        assertThat(rated.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(rated.getBody());
        assertThat(body.get("score").asInt()).isEqualTo(77);
        assertThat(body.get("level").asText()).isEqualTo("MID_HIGH");
        assertThat(body.get("status").asText()).isEqualTo("RATED");

        ResponseEntity<String> queried = exchangeGet("/bff/api/v1/merchants/" + merchantId + "/rating", headers);
        JsonNode queryBody = objectMapper.readTree(queried.getBody());
        assertThat(queryBody.get("score").asInt()).isEqualTo(77);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> exchangeGet(String path, HttpHeaders headers) {
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(baseUrl() + path, HttpMethod.GET, entity, String.class);
    }

    private void cleanupMarkerData() {
        testData.deleteMerchantRatingsByIdPattern(MARKER + "%");
    }
}
