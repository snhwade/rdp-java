package com.riskplatform.indicator.domain;

import java.util.List;
import java.util.Optional;

/**
 * 指标切片存储端口（六边形架构 Port）。
 *
 * <p>由基础设施层提供 Redis 实现（任务 7.x）。本端口聚焦"按 key 读写切片值"，
 * 窗口聚合与读路由由领域/应用服务编排。
 */
public interface SliceStore {

    /** 写入/覆盖单个切片值，并设置 TTL（秒，用于窗口老化 R8.7）。 */
    void writeSlice(String key, double value, long ttlSeconds);

    /** 对切片做原子增量累加，并刷新 TTL。 */
    void incrementSlice(String key, double increment, long ttlSeconds);

    /** 读取单个切片值。 */
    Optional<Double> readSlice(String key);

    /** 批量读取多个切片 key 的值（缺失的 key 不返回）。 */
    List<Double> readSlices(List<String> keys);

    /** 幂等去重标记：设置成功返回 true（首次），已存在返回 false。 */
    boolean markProcessedIfAbsent(String dedupKey, long ttlSeconds);
}
