package com.riskplatform.indicator.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
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
 * 指标写入读取集成测试（R13.2）：POST 写入后 GET 可读回 Redis 切片。
 */
class IndicatorWriteReadIntegrationTest extends AbstractIndicatorStoreMySqlRedisIntegrationTest {

    private static final String MARKER = "ZZIT_WR_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String refName;
    private String dimensionKey;

    @BeforeEach
    void setUp() {
        String runId = Long.toString(System.nanoTime());
        refName = MARKER + runId;
        dimensionKey = "merchant-M-" + runId;
    }

    @AfterEach
    void tearDown() {
        // 切片 TTL 较长，测试数据以 MARKER 前缀隔离即可
    }

    @Test
    void writeThenRead_returnsStoredValue() throws Exception {
        Map<String, Object> writeBody = new HashMap<>();
        writeBody.put("dimensionKey", dimensionKey);
        writeBody.put("value", 93.5);
        writeBody.put("granularity", "DAY");
        writeBody.put("source", "IT");

        ResponseEntity<String> writeResp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/indicators/" + refName,
                writeBody,
                String.class);
        assertThat(writeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(writeResp.getBody()).get("status").asText()).isEqualTo("OK");

        String readUrl = UriComponentsBuilder.fromHttpUrl("http://localhost:" + port + "/api/v1/indicators/" + refName)
                .queryParam("dimensionKey", dimensionKey)
                .queryParam("windowDays", 1)
                .queryParam("granularity", "DAY")
                .toUriString();

        ResponseEntity<String> readResp = restTemplate.getForEntity(readUrl, String.class);
        assertThat(readResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode readBody = objectMapper.readTree(readResp.getBody());
        assertThat(readBody.get("missing").asBoolean()).isFalse();
        assertThat(readBody.get("value").asDouble()).isEqualTo(93.5);
        assertThat(readBody.get("source").asText()).isIn("REDIS", "ES");
    }
}
