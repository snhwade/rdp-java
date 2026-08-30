package com.riskplatform.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.gateway.infrastructure.standalone.StandaloneListRecordMapper;
import com.riskplatform.gateway.infrastructure.standalone.StandaloneListRecordPO;
import com.riskplatform.gateway.integration.support.GatewayIntegrationTestDataMapper;
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

/**
 * 黑名单决策集成测试（R11/S1）：名单命中时网关返回 REJECT。
 */
class RiskEventBlacklistIntegrationTest extends AbstractGatewayMySqlRedisIntegrationTest {

    private static final String MARKER = "ZZIT_BL_";
    private static final String EVENT_TYPE = "B2B_RECV";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private GatewayIntegrationTestDataMapper testData;

    @Autowired
    private StandaloneListRecordMapper listRecordMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private String merchantId;
    private String eventId;
    private Long listRecordId;

    @BeforeEach
    void setUp() {
        merchantId = MARKER + System.nanoTime();
        listRecordId = seedBlackList(merchantId);
    }

    @AfterEach
    void tearDown() {
        if (listRecordId != null) {
            listRecordMapper.deleteById(listRecordId);
            listRecordId = null;
        }
        if (eventId != null) {
            testData.deleteEngineDecisionRecordByEventId(eventId);
            testData.deleteRiskOrderByEventId(eventId);
            eventId = null;
        }
    }

    @Test
    void acceptRiskEvent_blacklistHit_returnsReject() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("eventTypeCode", EVENT_TYPE);
        body.put("context", Map.of("merchantId", merchantId, "amount", 5000));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/risk-events", body, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode result = objectMapper.readTree(response.getBody());
        eventId = result.get("eventId").asText();
        assertThat(result.get("decision").asText()).isEqualTo("REJECT");
        assertThat(testData.countEngineDecisionRecordByEventId(eventId)).isEqualTo(1);
    }

    private long seedBlackList(String merchant) {
        StandaloneListRecordPO po = new StandaloneListRecordPO();
        po.setListType("BLACK");
        po.setDimension("merchantId");
        po.setDimensionValue(merchant);
        po.setEnabled(1);
        listRecordMapper.insert(po);
        return po.getId();
    }
}
