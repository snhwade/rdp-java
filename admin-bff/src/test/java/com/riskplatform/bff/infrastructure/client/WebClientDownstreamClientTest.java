package com.riskplatform.bff.infrastructure.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientDownstreamClientTest {

    @Test
    void describeTarget_mentionsActualService() {
        assertThat(WebClientDownstreamClient.describeTarget("http://localhost:8000", "/api/v1/ai/training-jobs"))
                .contains("ai-training-service(8000)")
                .contains("/api/v1/ai/training-jobs");
        assertThat(WebClientDownstreamClient.describeTarget("http://localhost:8082", "/x"))
                .contains("rule-config-service(8082)");
    }
}
