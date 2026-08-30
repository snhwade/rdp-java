package com.riskplatform.indicator.application.logical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** 从 rule-config 拉取上线逻辑指标，供虚拟 refName 读取聚合。 */
public class LogicalIndicatorProvider implements LogicalIndicatorCatalog {

    private static final Logger log = LoggerFactory.getLogger(LogicalIndicatorProvider.class);

    private final RestClient restClient;
    private final String ruleConfigBaseUrl;
    private final AtomicReference<List<LogicalIndicatorDefinition>> snapshot = new AtomicReference<>(List.of());

    public LogicalIndicatorProvider(RestClient restClient, String ruleConfigBaseUrl) {
        this.restClient = restClient;
        this.ruleConfigBaseUrl = ruleConfigBaseUrl;
    }

    @Override
    public List<LogicalIndicatorDefinition> current() {
        return snapshot.get();
    }

    @Override
    public Optional<LogicalIndicatorDefinition> findOnline(String refName) {
        return snapshot.get().stream()
                .filter(d -> d.refName().equals(refName))
                .findFirst();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void refresh() {
        try {
            List<Map<String, Object>> list = restClient.get()
                    .uri(ruleConfigBaseUrl + "/api/v1/logical-indicators?status=ONLINE")
                    .retrieve()
                    .body(List.class);
            if (list == null) {
                return;
            }
            List<LogicalIndicatorDefinition> defs = list.stream().map(this::toDefinition).toList();
            snapshot.set(defs);
            log.info("逻辑指标定义已刷新，共 {} 个: {}", defs.size(),
                    defs.stream().map(LogicalIndicatorDefinition::refName).toList());
        } catch (Exception ex) {
            log.warn("拉取逻辑指标失败，保留上次快照（{} 个）。原因: {}",
                    snapshot.get().size(), ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private LogicalIndicatorDefinition toDefinition(Map<String, Object> m) {
        String refName = String.valueOf(m.get("refName"));
        String combineMode = String.valueOf(m.getOrDefault("combineMode", "SUM"));
        String combineExpression = m.get("combineExpression") == null
                ? null : String.valueOf(m.get("combineExpression"));
        List<Map<String, Object>> rawMembers = (List<Map<String, Object>>) m.getOrDefault("members", List.of());
        List<String> memberRefs = rawMembers.stream()
                .map(mm -> String.valueOf(mm.get("memberRefName")))
                .toList();
        return new LogicalIndicatorDefinition(refName, combineMode, combineExpression, memberRefs);
    }
}
