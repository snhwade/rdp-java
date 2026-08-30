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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 商户评级列表集成测试（R12.7）：分页列表可查到已评级商户。
 */
class MerchantRatingListIntegrationTest extends AbstractMerchantRatingMySqlIntegrationTest {

    private static final String MARKER = "ZZIT_MRL_";

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
    void list_returnsRatedMerchantAfterCompute() throws Exception {
        Map<String, Object> factors = new HashMap<>();
        factors.put("industry", 1.0);
        factors.put("region", 0.5);
        factors.put("history", 0.8);
        postJson("/api/v1/merchants/" + merchantId + "/rating", Map.of("factors", factors));

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl() + "/api/v1/merchant-ratings")
                .queryParam("merchantId", merchantId)
                .queryParam("status", "RATED")
                .toUriString();

        ResponseEntity<String> listed = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);

        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = objectMapper.readTree(listed.getBody());
        assertThat(page.get("total").asLong()).isGreaterThanOrEqualTo(1L);
        assertThat(page.get("data").findValuesAsText("merchantId")).contains(merchantId);
        assertThat(page.get("data").findValuesAsText("level")).contains("MID_HIGH");
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

    private ResponseEntity<String> postJson(String path, Object body) {
        HttpEntity<Object> entity = new HttpEntity<>(body, authHeaders());
        return restTemplate.exchange(baseUrl() + path, HttpMethod.POST, entity, String.class);
    }

    private void cleanupMarkerData() {
        testData.deleteMerchantRatingsByIdPattern(MARKER + "%");
    }
}
