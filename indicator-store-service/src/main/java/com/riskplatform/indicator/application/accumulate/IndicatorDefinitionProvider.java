package com.riskplatform.indicator.application.accumulate;

import com.riskplatform.indicator.domain.SliceGranularity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 指标定义动态提供者（新方案核心）。
 *
 * <p>指标定义的「真源」在规则配置服务（rule-config 的 indicator_definition 表，由管理页增删改）。
 * 本提供者从 rule-config 的 {@code GET /api/v1/indicator-definitions?status=ONLINE} 拉取
 * 最新上线指标定义并本地缓存，累计消费者据此路由与累计。
 *
 * <p>刷新时机：启动加载 + 定时刷新（默认 30s）+ 收到配置变更广播时主动刷新。
 * 拉取失败时保留上一次成功的快照（容错），不影响已生效的累计。
 */
public class IndicatorDefinitionProvider implements IndicatorDefinitionCatalog {

    private static final Logger log = LoggerFactory.getLogger(IndicatorDefinitionProvider.class);

    private final RestClient restClient;
    private final String ruleConfigBaseUrl;
    private final AtomicReference<List<IndicatorDefinition>> snapshot = new AtomicReference<>(List.of());

    public IndicatorDefinitionProvider(RestClient restClient, String ruleConfigBaseUrl) {
        this.restClient = restClient;
        this.ruleConfigBaseUrl = ruleConfigBaseUrl;
    }

    /** 当前生效的指标定义快照（仅上线指标）。 */
    @Override
    public List<IndicatorDefinition> current() {
        return snapshot.get();
    }

    /**
     * 从 rule-config 拉取最新上线指标定义并刷新本地快照。
     * 拉取失败时保留旧快照并记录告警，保证累计不中断。
     */
    @Override
    @SuppressWarnings("unchecked")
    public void refresh() {
        try {
            List<Map<String, Object>> list = restClient.get()
                    .uri(ruleConfigBaseUrl + "/api/v1/indicator-definitions?status=ONLINE")
                    .retrieve()
                    .body(List.class);
            if (list == null) {
                return;
            }
            List<IndicatorDefinition> defs = list.stream().map(this::toDefinition).toList();
            snapshot.set(defs);
            log.info("指标定义已刷新，共 {} 个上线指标: {}", defs.size(),
                    defs.stream().map(IndicatorDefinition::refName).toList());
        } catch (Exception ex) {
            log.warn("拉取指标定义失败，保留上次快照（{} 个）。原因: {}",
                    snapshot.get().size(), ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private IndicatorDefinition toDefinition(Map<String, Object> m) {
        String refName = String.valueOf(m.get("refName"));
        List<String> eventTypeCodes = (List<String>) m.getOrDefault("eventTypeCodes", List.of());
        List<String> dimensions = (List<String>) m.getOrDefault("dimensions", List.of());
        int windowDays = m.get("windowDays") == null ? 1 : ((Number) m.get("windowDays")).intValue();
        SliceGranularity granularity = SliceGranularity.valueOf(
                String.valueOf(m.getOrDefault("sliceGranularity", "DAY")));
        String accScript = String.valueOf(m.getOrDefault("accScript", "current + 1"));
        String status = String.valueOf(m.getOrDefault("status", "OFFLINE"));
        boolean online = "ONLINE".equals(status);
        return new IndicatorDefinition(refName, eventTypeCodes, dimensions, granularity, windowDays, accScript, online);
    }
}
