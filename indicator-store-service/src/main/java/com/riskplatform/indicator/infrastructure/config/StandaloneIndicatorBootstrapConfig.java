package com.riskplatform.indicator.infrastructure.config;

import com.riskplatform.indicator.infrastructure.standalone.DbIndicatorDefinitionProvider;
import com.riskplatform.indicator.infrastructure.standalone.DbLogicalIndicatorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** standalone 模式下启动时加载 DB 指标/逻辑指标定义。 */
@Configuration
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class StandaloneIndicatorBootstrapConfig {

    @Bean
    StandaloneIndicatorBootstrap standaloneIndicatorBootstrap(
            DbIndicatorDefinitionProvider indicatorProvider,
            DbLogicalIndicatorProvider logicalProvider) {
        indicatorProvider.refresh();
        logicalProvider.refresh();
        return new StandaloneIndicatorBootstrap();
    }

    static final class StandaloneIndicatorBootstrap {
    }
}
