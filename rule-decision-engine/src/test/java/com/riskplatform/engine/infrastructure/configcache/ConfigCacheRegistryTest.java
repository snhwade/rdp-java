package com.riskplatform.engine.infrastructure.configcache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigCacheRegistryTest {

    @Test
    void getOrLoad_cachesAndInvalidate() {
        ConfigCacheRegistry registry = new ConfigCacheRegistry();
        int[] loads = {0};
        String v1 = registry.getOrLoad("RULE_PACKAGE", "11", id -> {
            loads[0]++;
            return "pkg-" + id;
        });
        String v2 = registry.getOrLoad("RULE_PACKAGE", "11", id -> {
            loads[0]++;
            return "pkg-" + id;
        });
        assertThat(v1).isEqualTo("pkg-11");
        assertThat(v2).isEqualTo("pkg-11");
        assertThat(loads[0]).isEqualTo(1);

        registry.invalidate("RULE_PACKAGE", "11");
        registry.getOrLoad("RULE_PACKAGE", "11", id -> {
            loads[0]++;
            return "pkg-" + id;
        });
        assertThat(loads[0]).isEqualTo(2);
    }
}
