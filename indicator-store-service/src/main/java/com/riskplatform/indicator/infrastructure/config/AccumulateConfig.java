package com.riskplatform.indicator.infrastructure.config;

import com.riskplatform.indicator.application.StorageProperties;
import com.riskplatform.indicator.application.accumulate.AccumulateProperties;
import com.riskplatform.indicator.application.IndicatorStorageWriter;
import com.riskplatform.indicator.application.accumulate.IndicatorAccumulateService;
import com.riskplatform.indicator.application.accumulate.IndicatorDefinitionCatalog;
import com.riskplatform.indicator.application.accumulate.IndicatorDefinitionProvider;
import com.riskplatform.indicator.domain.SliceStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AccumulateProperties.class)
@ConditionalOnProperty(prefix = "indicator.accumulate", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AccumulateConfig {

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public IndicatorDefinitionProvider remoteIndicatorDefinitionProvider(
            @Value("${downstream.rule-config:http://localhost:8082}") String ruleConfigBaseUrl) {
        IndicatorDefinitionProvider provider = new IndicatorDefinitionProvider(RestClient.create(), ruleConfigBaseUrl);
        provider.refresh();
        return provider;
    }

    @Bean
    public IndicatorAccumulateService indicatorAccumulateService(
            SliceStore sliceStore,
            IndicatorStorageWriter storageWriter,
            StorageProperties storage,
            IndicatorDefinitionCatalog definitionCatalog,
            com.riskplatform.indicator.infrastructure.stats.IndicatorRuntimeStatsWriter runtimeStatsWriter) {
        return new IndicatorAccumulateService(sliceStore, storageWriter, storage, definitionCatalog, runtimeStatsWriter);
    }

    @Bean
    public IndicatorDefinitionRefresher indicatorDefinitionRefresher(IndicatorDefinitionCatalog catalog) {
        return new IndicatorDefinitionRefresher(catalog);
    }

    public static class IndicatorDefinitionRefresher {
        private final IndicatorDefinitionCatalog catalog;

        public IndicatorDefinitionRefresher(IndicatorDefinitionCatalog catalog) {
            this.catalog = catalog;
        }

        @Scheduled(fixedDelayString = "${indicator.accumulate.refresh-ms:30000}")
        public void refresh() {
            catalog.refresh();
        }
    }
}
