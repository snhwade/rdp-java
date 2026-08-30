package com.riskplatform.engine.infrastructure.client;

import com.riskplatform.engine.domain.indicator.IndicatorReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 指标读取客户端（引擎 → indicator-store，主决策编排用）。
 *
 * <p>规则表达式可引用指标（如 {@code ai_fraud_score}）。主决策编排在执行规则前，
 * 按维度键读取所需指标当前值并注入上下文，使规则可基于实时/AI 指标判定。
 *
 * <p>指标不可读时返回默认值（0.0）并记录，不阻断决策（R9.4 降级语义在 indicator-store 内实现）。
 */
public class IndicatorStoreClient implements IndicatorReader {

    private static final Logger log = LoggerFactory.getLogger(IndicatorStoreClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public IndicatorStoreClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    /**
     * 读取单个指标当前值。
     *
     * @param refName      指标引用名
     * @param dimensionKey 维度键（如商户号）
     * @param windowDays   窗口天数
     * @param granularity  切片粒度（MINUTE|HOUR|DAY）
     * @return 指标值；不可读时返回 0.0
     */
    @Override
    @SuppressWarnings("unchecked")
    public double read(String refName, String dimensionKey, int windowDays, String granularity) {
        try {
            Map<String, Object> resp = restClient.get()
                    .uri(baseUrl + "/api/v1/indicators/{ref}?dimensionKey={dim}&windowDays={w}&granularity={g}",
                            refName, dimensionKey, windowDays, granularity)
                    .retrieve()
                    .body(Map.class);
            if (resp != null && resp.get("value") instanceof Number n) {
                return n.doubleValue();
            }
            return 0.0;
        } catch (Exception ex) {
            log.warn("读取指标失败，按默认值 0 处理: ref={} dim={} 原因={}", refName, dimensionKey, ex.getMessage());
            return 0.0;
        }
    }
}
