package com.riskplatform.engine.adapter.configcache;

import com.riskplatform.engine.infrastructure.configcache.ConfigCacheRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 配置缓存运维接口（enhancement-plan T6）。
 */
@RestController
@RequestMapping("/api/v1/config-cache")
public class ConfigCacheController {

    private final ConfigCacheRegistry registry;

    public ConfigCacheController(ConfigCacheRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/stats")
    public Map<String, Integer> stats() {
        return registry.sizes();
    }

    @PostMapping("/invalidate")
    public Map<String, Object> invalidate(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "id", required = false) String id) {
        if (type == null || type.isBlank()) {
            registry.invalidateAll();
            return Map.of("ok", true, "scope", "ALL");
        }
        registry.invalidate(type, id == null ? "*" : id);
        return Map.of("ok", true, "type", type, "id", id == null ? "*" : id);
    }
}
