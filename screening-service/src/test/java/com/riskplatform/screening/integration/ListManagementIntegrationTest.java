package com.riskplatform.screening.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.screening.integration.support.IntegrationTestJwt;
import com.riskplatform.screening.integration.support.ScreeningIntegrationTestDataMapper;
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
 * 名单管理集成测试（S1）：CRUD 与 {@code GET /api/v1/lists/check} 命中判定。
 */
class ListManagementIntegrationTest extends AbstractScreeningMySqlIntegrationTest {

    private static final String MARKER = "ZZIT_LIST_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ScreeningIntegrationTestDataMapper testData;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String runId;
    private String dimensionValue;

    @BeforeEach
    void setUp() {
        token = IntegrationTestJwt.operatorToken();
        runId = Long.toString(System.nanoTime());
        dimensionValue = MARKER + "M_" + runId;
        cleanupMarkerData();
    }

    @AfterEach
    void tearDown() {
        cleanupMarkerData();
    }

    @Test
    void listCrud_andCheckHit_roundTripThroughRealMySql() throws Exception {
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("listType", "BLACK");
        createBody.put("dimension", "merchantId");
        createBody.put("dimensionValue", dimensionValue);
        createBody.put("reason", MARKER + runId);

        ResponseEntity<String> created = postJson("/api/v1/lists", createBody);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode createdNode = objectMapper.readTree(created.getBody());
        long id = createdNode.get("id").asLong();
        assertThat(createdNode.get("dimensionValue").asText()).isEqualTo(dimensionValue);

        String checkUrl = UriComponentsBuilder.fromHttpUrl(baseUrl() + "/api/v1/lists/check")
                .queryParam("dimension", "merchantId")
                .queryParam("value", dimensionValue)
                .toUriString();
        ResponseEntity<String> checkBeforeAuth = restTemplate.getForEntity(checkUrl, String.class);
        assertThat(checkBeforeAuth.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode checkNode = objectMapper.readTree(checkBeforeAuth.getBody());
        assertThat(checkNode.get("blackHit").asBoolean()).isTrue();
        assertThat(checkNode.get("blackRecords").size()).isGreaterThan(0);

        ResponseEntity<String> listed = getJson("/api/v1/lists?type=BLACK");
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listNode = objectMapper.readTree(listed.getBody());
        assertThat(listNode.isArray()).isTrue();
        assertThat(listNode.findValuesAsText("dimensionValue")).contains(dimensionValue);

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("dimensionValue", dimensionValue);
        updateBody.put("reason", MARKER + "UPD_" + runId);
        updateBody.put("enabled", true);
        ResponseEntity<String> updated = putJson("/api/v1/lists/" + id, updateBody);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(updated.getBody()).get("reason").asText())
                .isEqualTo(MARKER + "UPD_" + runId);

        ResponseEntity<String> deleted = deleteJson("/api/v1/lists/" + id);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> checkAfterDelete = restTemplate.getForEntity(checkUrl, String.class);
        JsonNode checkAfter = objectMapper.readTree(checkAfterDelete.getBody());
        assertThat(checkAfter.get("blackHit").asBoolean()).isFalse();
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

    private ResponseEntity<String> putJson(String path, Object body) {
        HttpEntity<Object> entity = new HttpEntity<>(body, authHeaders());
        return restTemplate.exchange(baseUrl() + path, HttpMethod.PUT, entity, String.class);
    }

    private ResponseEntity<String> deleteJson(String path) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
        return restTemplate.exchange(baseUrl() + path, HttpMethod.DELETE, entity, String.class);
    }

    private void cleanupMarkerData() {
        testData.deleteListRecordsByReasonPattern(MARKER + "%");
    }
}
