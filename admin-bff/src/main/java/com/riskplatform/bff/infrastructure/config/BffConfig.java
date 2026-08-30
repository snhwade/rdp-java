package com.riskplatform.bff.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.bff.application.BffAggregationService;
import com.riskplatform.bff.domain.DownstreamClient;
import com.riskplatform.bff.infrastructure.client.WebClientDownstreamClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * BFF 组件装配（R14.1/R14.2/R17.1）。
 */
@Configuration
@EnableConfigurationProperties(DownstreamProperties.class)
public class BffConfig {

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public WebClient bffWebClient() {
        return WebClient.builder().build();
    }

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public DownstreamClient remoteDownstreamClient(WebClient bffWebClient, ObjectMapper objectMapper) {
        return new WebClientDownstreamClient(bffWebClient, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
    public MockMvc standaloneMockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Bean
    public BffAggregationService bffAggregationService(DownstreamClient downstreamClient,
                                                       DownstreamProperties downstreamProperties) {
        return new BffAggregationService(downstreamClient, downstreamProperties);
    }
}
