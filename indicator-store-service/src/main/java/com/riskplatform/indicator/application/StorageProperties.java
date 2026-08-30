package com.riskplatform.indicator.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 指标存储路由配置（绑定 {@code indicator.storage.*}）。
 *
 * <p>控制读写是否落 Redis / ES，可独立开关，支持双写或单写单读。
 */
@ConfigurationProperties(prefix = "indicator.storage")
public class StorageProperties {

    /** 写入 Redis 切片（默认开启）。 */
    private boolean writeRedis = true;
    /** 写入 ES 文档（默认开启，与 Redis 双写）。 */
    private boolean writeEs = true;
    /** 读取时尝试 Redis 窗口聚合（默认开启，优先于 ES）。 */
    private boolean readRedis = true;
    /** 读取时尝试 ES 窗口聚合（默认开启，Redis 缺失/不可用时回退）。 */
    private boolean readEs = true;
    /**
     * 累计幂等去重是否使用 Redis（{@code dedup:*} 键）。
     * 默认与 {@link #writeRedis} 一致；仅写 ES 时可设为 false（接受重复消费风险）。
     */
    private Boolean dedupRedis = null;

    public boolean isWriteRedis() {
        return writeRedis;
    }

    public void setWriteRedis(boolean writeRedis) {
        this.writeRedis = writeRedis;
    }

    public boolean isWriteEs() {
        return writeEs;
    }

    public void setWriteEs(boolean writeEs) {
        this.writeEs = writeEs;
    }

    public boolean isReadRedis() {
        return readRedis;
    }

    public void setReadRedis(boolean readRedis) {
        this.readRedis = readRedis;
    }

    public boolean isReadEs() {
        return readEs;
    }

    public void setReadEs(boolean readEs) {
        this.readEs = readEs;
    }

    public Boolean getDedupRedis() {
        return dedupRedis;
    }

    public void setDedupRedis(Boolean dedupRedis) {
        this.dedupRedis = dedupRedis;
    }

    /** 幂等去重是否走 Redis。未显式配置时与 writeRedis 一致。 */
    public boolean isDedupRedis() {
        return dedupRedis != null ? dedupRedis : writeRedis;
    }

    public boolean hasWriteTarget() {
        return writeRedis || writeEs;
    }

    public boolean hasReadTarget() {
        return readRedis || readEs;
    }
}
