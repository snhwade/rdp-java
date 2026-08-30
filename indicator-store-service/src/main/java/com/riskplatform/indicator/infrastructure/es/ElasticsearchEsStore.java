package com.riskplatform.indicator.infrastructure.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.riskplatform.indicator.domain.EsStore;
import com.riskplatform.indicator.domain.SliceGranularity;
import com.riskplatform.indicator.domain.SliceKey;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Elasticsearch 指标存储实现（R9.1/R9.4/R9.5）。
 *
 * <p>索引 {@code indicator-*}，文档字段：refName / dimensionKey / sliceTs / value / orderId / updatedAt。
 * 读取按 {@code refName + dimensionKey} 过滤并限定 {@code sliceTs ∈ [now-window, now]} 范围，
 * 对 {@code value} 做 {@code sum} 聚合得到窗口当前值（与 Redis 切片聚合语义一致）。
 *
 * <p>当 ES 不可用（连接异常/IO 异常）时抛出 {@link EsUnavailableException}，
 * 由读路由 {@code IndicatorReadService} 据此返回"指标不可读取"（R9.4）。
 */
public class ElasticsearchEsStore implements EsStore {

    /** 写入索引名（按月分区可在此扩展，本实现统一写入别名 indicator-write）。 */
    private static final String WRITE_INDEX = "indicator-write";
    /** 读取索引匹配模式（覆盖全部 indicator-* 索引）。 */
    private static final String READ_INDEX_PATTERN = "indicator-*";
    /** value 求和聚合名。 */
    private static final String SUM_AGG = "value_sum";

    private final ElasticsearchClient client;

    public ElasticsearchEsStore(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void write(String refName, String dimensionKey, long sliceTs, double value, String orderId) {
        Map<String, Object> doc = Map.of(
                "refName", refName,
                "dimensionKey", dimensionKey,
                "sliceTs", sliceTs,
                "value", value,
                "orderId", orderId == null ? "" : orderId,
                "updatedAt", Instant.now().toEpochMilli());
        // 文档 id 采用 refName:dimensionKey:sliceTs 以保证同一切片幂等覆盖（最终一致 R9.7）。
        String docId = refName + ":" + dimensionKey + ":" + sliceTs;
        try {
            client.index(i -> i.index(WRITE_INDEX).id(docId).document(doc));
        } catch (IOException ex) {
            throw new EsUnavailableException("ES 写入失败: " + ex.getMessage());
        }
    }

    @Override
    public Optional<Double> readSlice(String refName, String dimensionKey, long sliceTs) {
        String docId = refName + ":" + dimensionKey + ":" + sliceTs;
        try {
            var resp = client.get(g -> g.index(WRITE_INDEX).id(docId), Map.class);
            if (!resp.found() || resp.source() == null) {
                return Optional.empty();
            }
            Object value = resp.source().get("value");
            if (value instanceof Number n) {
                return Optional.of(n.doubleValue());
            }
            return Optional.empty();
        } catch (IOException ex) {
            throw new EsUnavailableException("ES 切片读取失败: " + ex.getMessage());
        }
    }

    @Override
    public Optional<Double> readWindow(String refName, String dimensionKey, int windowDays,
                                       SliceGranularity granularity, Instant now) {
        List<Long> slices = SliceKey.windowSlices(windowDays, granularity, now);
        if (slices.isEmpty()) {
            return Optional.empty();
        }
        long minSlice = slices.get(0);
        long maxSlice = slices.get(slices.size() - 1);

        // 组合过滤：refName + dimensionKey 精确匹配，sliceTs 落在窗口范围内
        Query refNameQ = Query.of(q -> q.term(t -> t.field("refName").value(refName)));
        Query dimQ = Query.of(q -> q.term(t -> t.field("dimensionKey").value(dimensionKey)));
        Query rangeQ = Query.of(q -> q.range(r -> r.number(n -> n
                .field("sliceTs")
                .gte((double) minSlice)
                .lte((double) maxSlice))));

        try {
            SearchResponse<Void> resp = client.search(s -> s
                            .index(READ_INDEX_PATTERN)
                            .size(0)
                            .query(q -> q.bool(b -> b.filter(refNameQ, dimQ, rangeQ)))
                            .aggregations(SUM_AGG, a -> a.sum(sum -> sum.field("value"))),
                    Void.class);

            long total = resp.hits().total() == null ? 0L : resp.hits().total().value();
            if (total == 0L) {
                // 无匹配切片视为 ES 中不存在该指标值
                return Optional.empty();
            }
            double sum = resp.aggregations().get(SUM_AGG).sum().value();
            return Optional.of(sum);
        } catch (IOException ex) {
            throw new EsUnavailableException("ES 读取失败: " + ex.getMessage());
        }
    }
}
