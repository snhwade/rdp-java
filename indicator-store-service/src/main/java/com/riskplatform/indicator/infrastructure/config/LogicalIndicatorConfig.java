package com.riskplatform.indicator.infrastructure.config;

import com.riskplatform.indicator.application.logical.LogicalIndicatorCatalog;
import com.riskplatform.indicator.application.logical.LogicalIndicatorProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

@Configuration
public class LogicalIndicatorConfig {

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public LogicalIndicatorProvider remoteLogicalIndicatorProvider(
            @Value("${downstream.rule-config:http://localhost:8082}") String ruleConfigBaseUrl) {
        LogicalIndicatorProvider provider = new LogicalIndicatorProvider(RestClient.create(), ruleConfigBaseUrl);
        provider.refresh();
        return provider;
    }

    @Bean
    public LogicalIndicatorRefresher logicalIndicatorRefresher(LogicalIndicatorCatalog catalog) {
        return new LogicalIndicatorRefresher(catalog);
    }

    public static class LogicalIndicatorRefresher {
        private final LogicalIndicatorCatalog catalog;

        public LogicalIndicatorRefresher(LogicalIndicatorCatalog catalog) {
            this.catalog = catalog;
        }

        @Scheduled(fixedDelayString = "${indicator.accumulate.refresh-ms:30000}")
        public void refresh() {
            catalog.refresh();
        }
    }
}
