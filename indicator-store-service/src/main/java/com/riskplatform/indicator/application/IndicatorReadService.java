package com.riskplatform.indicator.application;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.riskplatform.indicator.application.logical.LogicalIndicatorDefinition;
import com.riskplatform.indicator.application.logical.LogicalIndicatorCatalog;
import com.riskplatform.indicator.domain.EsStore;
import com.riskplatform.indicator.domain.IndicatorReadResult;
import com.riskplatform.indicator.domain.RedisUnavailableException;
import com.riskplatform.indicator.domain.SliceGranularity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 指标读路由服务（R9.3/R9.4/R16.3）。
 *
 * <p>支持物理 refName 直接读取，以及逻辑指标虚拟 refName（方案 C：聚合多个物理成员）。
 */
public class IndicatorReadService {

    public interface RedisReader {
        Optional<Double> read(String refName, String dimensionKey, int windowDays,
                              SliceGranularity granularity, Instant now);
    }

    private final RedisReader redisReader;
    private final EsStore esStore;
    private final LogicalIndicatorCatalog logicalProvider;
    private final StorageProperties storage;
    private final AviatorEvaluatorInstance aviator = AviatorEvaluator.newInstance();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public IndicatorReadService(RedisReader redisReader, EsStore esStore,
                                LogicalIndicatorCatalog logicalProvider,
                                StorageProperties storage) {
        this.redisReader = redisReader;
        this.esStore = esStore;
        this.logicalProvider = logicalProvider;
        this.storage = storage;
    }

    public IndicatorReadResult read(String refName, String dimensionKey, int windowDays,
                                    SliceGranularity granularity, Instant now,
                                    Supplier<Double> defaultValueSupplier) {
        Optional<LogicalIndicatorDefinition> logical = logicalProvider.findOnline(refName);
        if (logical.isPresent()) {
            return readLogical(logical.get(), dimensionKey, windowDays, granularity, now, defaultValueSupplier);
        }
        return readPhysical(refName, dimensionKey, windowDays, granularity, now, defaultValueSupplier);
    }

    private IndicatorReadResult readLogical(LogicalIndicatorDefinition logical, String dimensionKey,
                                            int windowDays, SliceGranularity granularity, Instant now,
                                            Supplier<Double> defaultValueSupplier) {
        Map<String, Double> memberValues = new HashMap<>();
        boolean allMissing = true;
        for (String memberRef : logical.memberRefNames()) {
            IndicatorReadResult memberResult = readPhysical(
                    memberRef, dimensionKey, windowDays, granularity, now, () -> 0.0);
            memberValues.put(memberRef, memberResult.value());
            if (!memberResult.missing()) {
                allMissing = false;
            }
        }
        if (allMissing) {
            return IndicatorReadResult.defaultValue(defaultValueSupplier.get());
        }
        double combined = combine(logical, memberValues);
        return IndicatorReadResult.virtual(combined);
    }

    private double combine(LogicalIndicatorDefinition logical, Map<String, Double> memberValues) {
        if ("EXPRESSION".equalsIgnoreCase(logical.combineMode())
                && logical.combineExpression() != null
                && !logical.combineExpression().isBlank()) {
            Expression expr = expressionCache.computeIfAbsent(
                    logical.combineExpression(), s -> aviator.compile(s, true));
            Object result = expr.execute(new HashMap<>(memberValues));
            if (result instanceof Number n) {
                return n.doubleValue();
            }
            throw new IllegalStateException("组合表达式未返回数值: " + result);
        }
        return memberValues.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private IndicatorReadResult readPhysical(String refName, String dimensionKey, int windowDays,
                                             SliceGranularity granularity, Instant now,
                                             Supplier<Double> defaultValueSupplier) {
        if (!storage.hasReadTarget()) {
            return IndicatorReadResult.defaultValue(defaultValueSupplier.get());
        }
        if (storage.isReadRedis()) {
            try {
                Optional<Double> redisVal = redisReader.read(refName, dimensionKey, windowDays, granularity, now);
                if (redisVal.isPresent()) {
                    return IndicatorReadResult.fromRedis(redisVal.get());
                }
            } catch (RedisUnavailableException ignored) {
                // fall through
            }
        }
        if (storage.isReadEs()) {
            try {
                Optional<Double> esVal = esStore.readWindow(refName, dimensionKey, windowDays, granularity, now);
                if (esVal.isPresent()) {
                    return IndicatorReadResult.fromEs(esVal.get());
                }
            } catch (EsStore.EsUnavailableException ignored) {
                // fall through
            }
        }
        return IndicatorReadResult.defaultValue(defaultValueSupplier.get());
    }
}
