package com.riskplatform.rating.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.rating.integration.support.IntegrationTestJwt;
import com.riskplatform.rating.integration.support.RatingIntegrationTestDataMapper;
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
 * 商户评级集成测试（R12）：触发计算与查询经真实 MySQL 往返。
 */
class MerchantRatingIntegrationTest extends AbstractMerchantRatingMySqlIntegrationTest {

    private static final String MARKER = "ZZIT_MR_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RatingIntegrationTestDataMapper testData;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String merchantId;

    @BeforeEach
    void setUp() {
        token = IntegrationTestJwt.operatorToken();
        merchantId = MARKER + System.nanoTime();
        cleanupMarkerData();
    }

    @AfterEach
    void tearDown() {
        cleanupMarkerData();
    }

    @Test
    void computeAndQuery_roundTripThroughRealMySql() throws Exception {
        ResponseEntity<String> unrated = getJson("/api/v1/merchants/" + merchantId + "/rating");
        assertThat(unrated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode unratedBody = objectMapper.readTree(unrated.getBody());
        assertThat(unratedBody.get("status").asText()).isEqualTo("UNRATED");

        Map<String, Object> factors = new HashMap<>();
        factors.put("industry", 1.0);
        factors.put("region", 0.5);
        factors.put("history", 0.8);

        ResponseEntity<String> computed = postJson("/api/v1/merchants/" + merchantId + "/rating",
                Map.of("factors", factors));
        assertThat(computed.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode rated = objectMapper.readTree(computed.getBody());
        assertThat(rated.get("merchantId").asText()).isEqualTo(merchantId);
        assertThat(rated.get("score").asInt()).isEqualTo(77);
        assertThat(rated.get("level").asText()).isEqualTo("MID_HIGH");
        assertThat(rated.get("status").asText()).isEqualTo("RATED");

        assertThat(testData.findScoreByMerchantId(merchantId)).isEqualTo(77);
        assertThat(testData.findLevelByMerchantId(merchantId)).isEqualTo("MID_HIGH");

        ResponseEntity<String> queried = getJson("/api/v1/merchants/" + merchantId + "/rating");
        JsonNode queryBody = objectMapper.readTree(queried.getBody());
        assertThat(queryBody.get("score").asInt()).isEqualTo(77);
        assertThat(queryBody.get("level").asText()).isEqualTo("MID_HIGH");
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> getJson(String path) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
        return restTemplate.exchange(baseUrl() + path, HttpMethod.GET, entity, String.class);
    }

    private ResponseEntity<String> postJson(String path, Object body) {
        HttpEntity<Object> entity = new HttpEntity<>(body, authHeaders());
        return restTemplate.exchange(baseUrl() + path, HttpMethod.POST, entity, String.class);
    }

    private void cleanupMarkerData() {
        testData.deleteMerchantRatingsByIdPattern(MARKER + "%");
    }
}
