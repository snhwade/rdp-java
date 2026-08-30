package com.riskplatform.indicator.infrastructure.redis;

import com.riskplatform.indicator.application.IndicatorReadService;
import com.riskplatform.indicator.domain.RedisUnavailableException;
import com.riskplatform.indicator.domain.SliceGranularity;
import com.riskplatform.indicator.domain.SliceKey;
import com.riskplatform.indicator.domain.SliceStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Redis 读取适配器（R9.3/R9.4）。
 *
 * <p>将 Redis 切片窗口聚合适配为读路由所需的 {@link IndicatorReadService.RedisReader} 契约：
 * <ul>
 *   <li>窗口内存在任一切片 → 返回切片求和的聚合值（命中）；</li>
 *   <li>窗口内无任何切片 → 返回 {@link Optional#empty()}（缺失，触发 ES 回退）；</li>
 *   <li>Redis 不可用 → 抛出 {@link RedisUnavailableException}（触发 ES 回退）。</li>
 * </ul>
 *
 * <p>说明：领域聚合器对"无切片"会返回 0.0，无法与"真实值为 0"区分。为正确驱动 R9.4 的
 * 回退语义，此处直接探测窗口内是否存在切片：存在则对已取回的切片求和返回，否则返回缺失。
 */
public class AggregatingRedisReader implements IndicatorReadService.RedisReader {

    private final SliceStore sliceStore;

    public AggregatingRedisReader(SliceStore sliceStore) {
        this.sliceStore = sliceStore;
    }

    @Override
    public Optional<Double> read(String refName, String dimensionKey, int windowDays,
                                 SliceGranularity granularity, Instant now) {
        // 计算 [now-window, now] 窗口内的切片 key 列表
        List<String> keys = SliceKey.windowSlices(windowDays, granularity, now).stream()
                .map(ts -> SliceKey.of(refName, dimensionKey, granularity, ts))
                .toList();

        // 批量读取窗口内切片（Redis 不可用会抛 RedisUnavailableException，由读路由回退 ES）
        List<Double> values = sliceStore.readSlices(keys);
        if (values.isEmpty()) {
            // 窗口内无任何切片视为 Redis 缺失，触发 ES 回退
            return Optional.empty();
        }
        // 命中：对窗口内切片求和得到当前值
        return Optional.of(values.stream().mapToDouble(Double::doubleValue).sum());
    }
}
