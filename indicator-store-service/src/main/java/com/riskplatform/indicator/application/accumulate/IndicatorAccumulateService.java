package com.riskplatform.indicator.application.accumulate;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.riskplatform.indicator.application.IndicatorStorageWriter;
import com.riskplatform.indicator.application.StorageProperties;
import com.riskplatform.indicator.domain.RedisUnavailableException;
import com.riskplatform.indicator.domain.SliceKey;
import com.riskplatform.indicator.domain.SliceStore;
import com.riskplatform.indicator.infrastructure.stats.IndicatorRuntimeStatsWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标累计服务（新方案核心，R8.2/R8.3/R8.5/R8.6/R8.7）。
 *
 * <p>对一条订单终态数据：
 * <ol>
 *   <li>路由：匹配「所有统计维度字段均存在」的指标定义（缺字段则该指标跳过，R8.2）；</li>
 *   <li>幂等：按 orderId+refName 做 Redis 去重，重复消费指标值不变（R8.6）；</li>
 *   <li>切片：按事件时间与粒度截断得到切片起点；</li>
 *   <li>累计：读当前切片值 → Aviator 累计脚本求新值（脚本异常跳过+告警，R8.5）；</li>
 *   <li>写入：按 {@code indicator.storage} 配置双写 Redis / ES（R8.7）。</li>
 * </ol>
 *
 * <p>切片 Key 与读取路径（IndicatorReadService/SliceKey）完全一致，写入后即可被规则引擎读到。
 */
public class IndicatorAccumulateService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorAccumulateService.class);

    private final SliceStore sliceStore;
    private final IndicatorStorageWriter storageWriter;
    private final StorageProperties storage;
    private final IndicatorDefinitionCatalog definitionProvider;
    private final AviatorEvaluatorInstance aviator = AviatorEvaluator.newInstance();
    private final Map<String, Expression> scriptCache = new ConcurrentHashMap<>();
    private final IndicatorRuntimeStatsWriter runtimeStatsWriter;

    public IndicatorAccumulateService(SliceStore sliceStore,
                                      IndicatorStorageWriter storageWriter,
                                      StorageProperties storage,
                                      IndicatorDefinitionCatalog definitionProvider,
                                      IndicatorRuntimeStatsWriter runtimeStatsWriter) {
        this.sliceStore = sliceStore;
        this.storageWriter = storageWriter;
        this.storage = storage;
        this.definitionProvider = definitionProvider;
        this.runtimeStatsWriter = runtimeStatsWriter;
    }

    /**
     * 处理一条订单终态数据，对所有适用指标累计。
     *
     * @return 实际累计的指标数（用于观测/测试）
     */
    public int accumulate(OrderFinalState order) {
        if (order == null || order.getOrderId() == null || order.getOrderId().isBlank()) {
            return 0; // 无 orderId 无法幂等，跳过（反序列化层已拦截，双保险）
        }
        int applied = 0;
        for (IndicatorDefinition def : definitionProvider.current()) {
            if (!def.online()) {
                continue;
            }
            if (!def.matchesEvent(order.getEventTypeCode())) {
                continue;
            }
            if (!hasAllDimensions(def, order)) {
                continue; // R8.2：维度字段不全，该指标不适用
            }
            if (accumulateOne(def, order)) {
                applied++;
            }
        }
        return applied;
    }

    private boolean accumulateOne(IndicatorDefinition def, OrderFinalState order) {
        String dimensionKey = dimensionKey(def, order);
        // R8.6 幂等：orderId + refName 维度去重，TTL 与窗口一致
        String dedupKey = "dedup:" + def.refName() + ":" + order.getOrderId();
        try {
            if (storage.isDedupRedis()) {
                if (!sliceStore.markProcessedIfAbsent(dedupKey, def.ttlSeconds())) {
                    return false;
                }
            }
        } catch (RedisUnavailableException e) {
            log.warn("幂等标记失败（Redis 不可用），跳过累计: {} order={}", def.refName(), order.getOrderId());
            return false;
        }

        long sliceTs = def.granularity().truncateToSlice(Instant.ofEpochMilli(order.getEventEpochMs()));
        String sliceKey = SliceKey.of(def.refName(), dimensionKey, def.granularity(), sliceTs);

        double current;
        try {
            current = sliceStore.readSlice(sliceKey).orElse(0d);
        } catch (RedisUnavailableException e) {
            log.warn("读取当前切片失败（Redis 不可用），跳过累计: {}", sliceKey);
            return false;
        }

        double newValue;
        try {
            newValue = evaluate(def.accScript(), current, order);
        } catch (Exception e) {
            // R8.5：累计脚本异常 → 跳过该消息并告警，不影响其它指标
            log.warn("累计脚本异常，跳过: 指标 {} 脚本 [{}] order={} 原因={}",
                    def.refName(), def.accScript(), order.getOrderId(), e.getMessage());
            return false;
        }

        try {
            storageWriter.writeSlice(sliceKey, newValue, def.ttlSeconds(), order.getOrderId());
        } catch (RuntimeException e) {
            log.warn("写入切片失败: {} 原因={}", sliceKey, e.getMessage());
            return false;
        }
        log.debug("累计完成 {} = {} (slice={})", sliceKey, newValue, sliceTs);
        runtimeStatsWriter.recordAccumulate(def.refName());
        return true;
    }

    /** 维度键：单维度取其值（如 M001），多维度按 dim#val;dim#val 拼接，与读取侧约定一致。 */
    static String dimensionKey(IndicatorDefinition def, OrderFinalState order) {
        List<String> dims = def.dimensions();
        Map<String, Object> fields = order.getFields();
        if (dims.size() == 1) {
            return String.valueOf(fields.get(dims.get(0)));
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String dim : dims) {
            if (!first) {
                sb.append(';');
            }
            sb.append(dim).append('#').append(fields.get(dim));
            first = false;
        }
        return sb.toString();
    }

    private boolean hasAllDimensions(IndicatorDefinition def, OrderFinalState order) {
        Map<String, Object> fields = order.getFields();
        if (fields == null) {
            return def.dimensions().isEmpty();
        }
        for (String dim : def.dimensions()) {
            if (!fields.containsKey(dim) || fields.get(dim) == null) {
                return false;
            }
        }
        return true;
    }

    private double evaluate(String accScript, double current, OrderFinalState order) {
        Map<String, Object> env = new java.util.HashMap<>();
        env.put("current", current);
        if (order.getFields() != null) {
            env.putAll(order.getFields());
        }
        Expression expr = scriptCache.computeIfAbsent(accScript, s -> aviator.compile(s, true));
        Object result = expr.execute(env);
        if (result instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalStateException("累计脚本未返回数值: " + result);
    }
}
