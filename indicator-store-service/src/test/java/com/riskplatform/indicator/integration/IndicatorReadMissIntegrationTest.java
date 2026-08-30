package com.riskplatform.indicator.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.indicator.integration.support.IndicatorStoreIntegrationTestDataMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 指标读缺失集成测试（IS1）：GET /api/v1/indicators/{refName} 两源不可读时
 * {@code missing=true} 并递增 {@code indicator_runtime_stats.read_miss_count}。
 */
class IndicatorReadMissIntegrationTest extends AbstractIndicatorStoreMySqlRedisIntegrationTest {

    private static final String MARKER = "ZZIT_MISS_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private IndicatorStoreIntegrationTestDataMapper testData;

    @Autowired
    private ObjectMapper objectMapper;

    private String runId;
    private String refName;

    @BeforeEach
    void setUp() {
        runId = Long.toString(System.nanoTime());
        refName = MARKER + runId;
        cleanupMarkerData();
    }

    @AfterEach
    void tearDown() {
        cleanupMarkerData();
    }

    @Test
    void readMissing_incrementsRuntimeStatsReadMissCount() throws Exception {
        Long before = testData.findReadMissCount(refName);

        String url = UriComponentsBuilder.fromHttpUrl("http://localhost:" + port + "/api/v1/indicators/" + refName)
                .queryParam("dimensionKey", "merchant#M_NO_DATA")
                .queryParam("windowDays", 1)
                .queryParam("granularity", "DAY")
                .toUriString();

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("refName").asText()).isEqualTo(refName);
        assertThat(body.get("missing").asBoolean()).isTrue();

        Long after = testData.findReadMissCount(refName);
        long baseline = before == null ? 0L : before;
        assertThat(after).isNotNull();
        assertThat(after).isEqualTo(baseline + 1);
    }

    private void cleanupMarkerData() {
        testData.deleteRuntimeStatsByRefNamePattern(MARKER + "%");
    }
}
