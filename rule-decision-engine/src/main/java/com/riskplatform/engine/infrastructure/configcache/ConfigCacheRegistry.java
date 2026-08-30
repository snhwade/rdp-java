package com.riskplatform.engine.infrastructure.configcache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 引擎侧配置本地缓存（enhancement-plan T6）。
 * Kafka 变更消息到达后按类型/ID 失效；未命中缓存时回源加载。
 */
@Component
public class ConfigCacheRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConfigCacheRegistry.class);

    private final Map<String, Map<String, Object>> caches = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String type, String id, Function<String, T> loader) {
        String key = normalize(type);
        Map<String, Object> bucket = caches.computeIfAbsent(key, t -> new ConcurrentHashMap<>());
        Object cached = bucket.get(id);
        if (cached != null) {
            return (T) cached;
        }
        T loaded = loader.apply(id);
        if (loaded != null) {
            bucket.put(id, loaded);
        }
        return loaded;
    }

    public void invalidate(String type, String id) {
        String key = normalize(type);
        Map<String, Object> bucket = caches.get(key);
        if (bucket == null) {
            log.info("配置缓存失效（无桶）: type={} id={}", type, id);
            return;
        }
        if (id == null || id.isBlank() || "*".equals(id)) {
            bucket.clear();
            log.info("配置缓存全量失效: type={}", type);
            return;
        }
        bucket.remove(id);
        log.info("配置缓存失效: type={} id={}", type, id);
    }

    public void invalidateAll() {
        caches.values().forEach(Map::clear);
        log.info("配置缓存全部清空");
    }

    public Map<String, Integer> sizes() {
        Map<String, Integer> out = new ConcurrentHashMap<>();
        caches.forEach((k, v) -> out.put(k, v.size()));
        return out;
    }

    private static String normalize(String type) {
        if (type == null || type.isBlank()) {
            return "UNKNOWN";
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }
}
