package com.riskplatform.gateway.infrastructure.decisionlog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.model.PagedResult;
import com.riskplatform.gateway.domain.AiAdviseResult;
import com.riskplatform.gateway.domain.AiDecisionRecordView;
import com.riskplatform.gateway.domain.AiDecisionStatsView;
import com.riskplatform.gateway.domain.DecisionExecutionLogStore;
import com.riskplatform.gateway.domain.DecisionRecordQuery;
import com.riskplatform.gateway.domain.EngineDecisionRecordView;
import com.riskplatform.gateway.domain.EngineDecisionStatsView;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 引擎与 AI 决策执行记录 MySQL 实现。
 */
public class MySqlDecisionExecutionLogRepository implements DecisionExecutionLogStore {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> TRACE_TYPE = new TypeReference<>() {};

    private final EngineDecisionRecordMapper engineMapper;
    private final AiDecisionRecordMapper aiMapper;
    private final ObjectMapper objectMapper;

    public MySqlDecisionExecutionLogRepository(
            EngineDecisionRecordMapper engineMapper,
            AiDecisionRecordMapper aiMapper,
            ObjectMapper objectMapper) {
        this.engineMapper = engineMapper;
        this.aiMapper = aiMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveEngineRecord(
            String eventId,
            String correlationId,
            String businessOrderId,
            String merchantId,
            String eventTypeCode,
            long eventTimeMs,
            String engineDecision,
            String finalDecision,
            String invokeMode,
            Long rulePackageId,
            Long decisionFlowId,
            Map<String, Object> detail,
            Long elapsedMs) {
        EngineDecisionRecordPO existing = findEnginePo(eventId);
        EngineDecisionRecordPO po = existing != null ? existing : new EngineDecisionRecordPO();
        po.setEventId(eventId);
        po.setCorrelationId(correlationId);
        po.setBusinessOrderId(businessOrderId);
        po.setMerchantId(merchantId);
        po.setEventTypeCode(eventTypeCode);
        po.setEventTime(toLocalDateTime(eventTimeMs));
        po.setEngineDecision(engineDecision);
        po.setFinalDecision(finalDecision);
        po.setInvokeMode(invokeMode);
        po.setRulePackageId(rulePackageId);
        po.setDecisionFlowId(decisionFlowId);
        po.setDetailJson(writeJson(detail));
        po.setElapsedMs(elapsedMs);
        if (existing == null) {
            engineMapper.insert(po);
        } else {
            engineMapper.updateById(po);
        }
    }

    @Override
    public void createAiPending(
            String eventId,
            String correlationId,
            String businessOrderId,
            String merchantId,
            String eventTypeCode,
            long eventTimeMs,
            String engineDecision) {
        AiDecisionRecordPO existing = findAiPo(eventId);
        if (existing != null) {
            return;
        }
        AiDecisionRecordPO po = new AiDecisionRecordPO();
        po.setEventId(eventId);
        po.setCorrelationId(correlationId);
        po.setBusinessOrderId(businessOrderId);
        po.setMerchantId(merchantId);
        po.setEventTypeCode(eventTypeCode);
        po.setEventTime(toLocalDateTime(eventTimeMs));
        po.setStatus("PENDING");
        po.setEngineDecision(engineDecision);
        aiMapper.insert(po);
    }

    @Override
    public void completeAiSuccess(String eventId, AiAdviseResult result, String engineDecision) {
        AiDecisionRecordPO po = findAiPo(eventId);
        if (po == null || !"PENDING".equals(po.getStatus())) {
            return;
        }
        po.setStatus("SUCCESS");
        po.setAgentDecision(result.decision());
        po.setConfidence(result.confidence());
        po.setReason(result.reason());
        po.setEngineDecision(engineDecision);
        po.setDivergence(isDivergent(engineDecision, result.decision()));
        po.setTraceJson(writeJson(result.trace()));
        po.setCompletedAt(LocalDateTime.now());
        aiMapper.updateById(po);
    }

    @Override
    public void completeAiFailed(String eventId, String failReason) {
        AiDecisionRecordPO po = findAiPo(eventId);
        if (po == null || !"PENDING".equals(po.getStatus())) {
            return;
        }
        po.setStatus("FAILED");
        po.setFailReason(failReason);
        po.setCompletedAt(LocalDateTime.now());
        aiMapper.updateById(po);
    }

    @Override
    public PagedResult<EngineDecisionRecordView> queryEngine(DecisionRecordQuery query) {
        LambdaQueryWrapper<EngineDecisionRecordPO> wrapper = buildEngineWrapper(query);
        wrapper.orderByDesc(EngineDecisionRecordPO::getEventTime)
                .orderByDesc(EngineDecisionRecordPO::getCreatedAt);
        Page<EngineDecisionRecordPO> page = Page.of(query.page(), query.pageSize());
        IPage<EngineDecisionRecordPO> result = engineMapper.selectPage(page, wrapper);
        List<EngineDecisionRecordView> views = result.getRecords().stream().map(this::toEngineView).toList();
        return PagedResult.of(views, query.page(), query.pageSize(), result.getTotal());
    }

    @Override
    public PagedResult<AiDecisionRecordView> queryAi(DecisionRecordQuery query) {
        LambdaQueryWrapper<AiDecisionRecordPO> wrapper = buildAiWrapper(query);
        wrapper.orderByDesc(AiDecisionRecordPO::getCompletedAt)
                .orderByDesc(AiDecisionRecordPO::getCreatedAt);
        Page<AiDecisionRecordPO> page = Page.of(query.page(), query.pageSize());
        IPage<AiDecisionRecordPO> result = aiMapper.selectPage(page, wrapper);
        List<AiDecisionRecordView> views = result.getRecords().stream().map(this::toAiView).toList();
        return PagedResult.of(views, query.page(), query.pageSize(), result.getTotal());
    }

    @Override
    public EngineDecisionRecordView findEngineByEventId(String eventId) {
        EngineDecisionRecordPO po = findEnginePo(eventId);
        return po == null ? null : toEngineView(po);
    }

    @Override
    public AiDecisionRecordView findAiByEventId(String eventId) {
        AiDecisionRecordPO po = findAiPo(eventId);
        return po == null ? null : toAiView(po);
    }

    @Override
    public Map<String, AiDecisionRecordView> findAiByEventIds(Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = eventIds.stream().filter(MySqlDecisionExecutionLogRepository::notBlank).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<AiDecisionRecordPO> rows = aiMapper.selectList(new LambdaQueryWrapper<AiDecisionRecordPO>()
                .in(AiDecisionRecordPO::getEventId, ids));
        return rows.stream()
                .map(this::toAiView)
                .collect(Collectors.toMap(AiDecisionRecordView::eventId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    private LambdaQueryWrapper<EngineDecisionRecordPO> buildEngineWrapper(DecisionRecordQuery query) {
        LambdaQueryWrapper<EngineDecisionRecordPO> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(query.eventId())) {
            wrapper.eq(EngineDecisionRecordPO::getEventId, query.eventId());
        }
        if (notBlank(query.correlationId())) {
            wrapper.eq(EngineDecisionRecordPO::getCorrelationId, query.correlationId());
        }
        if (notBlank(query.businessOrderId())) {
            wrapper.and(w -> w.eq(EngineDecisionRecordPO::getBusinessOrderId, query.businessOrderId())
                    .or()
                    .eq(EngineDecisionRecordPO::getEventId, query.businessOrderId()));
        }
        if (notBlank(query.merchantId())) {
            wrapper.eq(EngineDecisionRecordPO::getMerchantId, query.merchantId());
        }
        if (notBlank(query.eventTypeCode())) {
            wrapper.eq(EngineDecisionRecordPO::getEventTypeCode, query.eventTypeCode());
        }
        if (query.startTimeMs() != null) {
            wrapper.ge(EngineDecisionRecordPO::getEventTime, toLocalDateTime(query.startTimeMs()));
        }
        if (query.endTimeMs() != null) {
            wrapper.le(EngineDecisionRecordPO::getEventTime, toLocalDateTime(query.endTimeMs()));
        }
        return wrapper;
    }

    private LambdaQueryWrapper<AiDecisionRecordPO> buildAiWrapper(DecisionRecordQuery query) {
        LambdaQueryWrapper<AiDecisionRecordPO> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(query.eventId())) {
            wrapper.eq(AiDecisionRecordPO::getEventId, query.eventId());
        }
        if (notBlank(query.correlationId())) {
            wrapper.eq(AiDecisionRecordPO::getCorrelationId, query.correlationId());
        }
        if (notBlank(query.merchantId())) {
            wrapper.eq(AiDecisionRecordPO::getMerchantId, query.merchantId());
        }
        if (notBlank(query.eventTypeCode())) {
            wrapper.eq(AiDecisionRecordPO::getEventTypeCode, query.eventTypeCode());
        }
        if (query.startTimeMs() != null) {
            wrapper.ge(AiDecisionRecordPO::getEventTime, toLocalDateTime(query.startTimeMs()));
        }
        if (query.endTimeMs() != null) {
            wrapper.le(AiDecisionRecordPO::getEventTime, toLocalDateTime(query.endTimeMs()));
        }
        if (Boolean.TRUE.equals(query.divergenceOnly())) {
            wrapper.eq(AiDecisionRecordPO::getDivergence, true);
        }
        return wrapper;
    }

    @Override
    public AiDecisionStatsView queryAiStats(Long startTimeMs, Long endTimeMs, String eventTypeCode) {
        LambdaQueryWrapper<AiDecisionRecordPO> base = new LambdaQueryWrapper<>();
        if (startTimeMs != null) {
            base.ge(AiDecisionRecordPO::getEventTime, toLocalDateTime(startTimeMs));
        }
        if (endTimeMs != null) {
            base.le(AiDecisionRecordPO::getEventTime, toLocalDateTime(endTimeMs));
        }
        if (notBlank(eventTypeCode)) {
            base.eq(AiDecisionRecordPO::getEventTypeCode, eventTypeCode);
        }

        long total = aiMapper.selectCount(base);
        long success = aiMapper.selectCount(base.clone().eq(AiDecisionRecordPO::getStatus, "SUCCESS"));
        long failed = aiMapper.selectCount(base.clone().eq(AiDecisionRecordPO::getStatus, "FAILED"));
        long pending = aiMapper.selectCount(base.clone().eq(AiDecisionRecordPO::getStatus, "PENDING"));
        long divergenceCount = aiMapper.selectCount(base.clone().eq(AiDecisionRecordPO::getDivergence, true));
        double rate = total == 0 ? 0.0 : (double) divergenceCount / (double) total;
        double failRate = total == 0 ? 0.0 : (double) failed / (double) total;

        List<AiDecisionRecordPO> failedRows = aiMapper.selectList(
                base.clone().eq(AiDecisionRecordPO::getStatus, "FAILED")
                        .select(AiDecisionRecordPO::getFailReason));
        long timedOut = failedRows.stream().filter(po -> isTimeoutReason(po.getFailReason())).count();

        List<AiDecisionRecordPO> typed = aiMapper.selectList(
                base.clone().select(AiDecisionRecordPO::getEventTypeCode, AiDecisionRecordPO::getDivergence));
        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (AiDecisionRecordPO po : typed) {
            String code = po.getEventTypeCode() == null ? "" : po.getEventTypeCode();
            long[] pair = buckets.computeIfAbsent(code, k -> new long[2]);
            pair[0]++;
            if (Boolean.TRUE.equals(po.getDivergence())) {
                pair[1]++;
            }
        }
        List<AiDecisionStatsView.EventTypeBucket> byType = buckets.entrySet().stream()
                .map(e -> new AiDecisionStatsView.EventTypeBucket(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Long.compare(b.total(), a.total()))
                .toList();

        Map<String, Long> adoptionCounts = new LinkedHashMap<>();
        long modelScoreCalls = 0;
        long modelScoreAvailable = 0;
        LambdaQueryWrapper<EngineDecisionRecordPO> engineBase = new LambdaQueryWrapper<>();
        if (startTimeMs != null) {
            engineBase.ge(EngineDecisionRecordPO::getEventTime, toLocalDateTime(startTimeMs));
        }
        if (endTimeMs != null) {
            engineBase.le(EngineDecisionRecordPO::getEventTime, toLocalDateTime(endTimeMs));
        }
        if (notBlank(eventTypeCode)) {
            engineBase.eq(EngineDecisionRecordPO::getEventTypeCode, eventTypeCode);
        }
        List<EngineDecisionRecordPO> engineRows = engineMapper.selectList(
                engineBase.select(EngineDecisionRecordPO::getDetailJson).last("LIMIT 5000"));
        for (EngineDecisionRecordPO row : engineRows) {
            Map<String, Object> detail = readMap(row.getDetailJson());
            if (detail == null) {
                continue;
            }
            Object mode = detail.get("adoptionMode");
            String modeKey = mode == null || String.valueOf(mode).isBlank()
                    ? "UNKNOWN" : String.valueOf(mode).toUpperCase(Locale.ROOT);
            adoptionCounts.merge(modeKey, 1L, Long::sum);
            for (Map.Entry<String, Object> e : detail.entrySet()) {
                String key = e.getKey();
                if (key != null && key.endsWith("_available")) {
                    modelScoreCalls++;
                    if (Boolean.TRUE.equals(e.getValue()) || "true".equalsIgnoreCase(String.valueOf(e.getValue()))) {
                        modelScoreAvailable++;
                    }
                }
            }
            Object nested = detail.get("assignments");
            if (nested instanceof Map<?, ?> assign) {
                for (Map.Entry<?, ?> e : assign.entrySet()) {
                    String key = e.getKey() == null ? null : String.valueOf(e.getKey());
                    if (key != null && key.endsWith("_available")) {
                        modelScoreCalls++;
                        if (Boolean.TRUE.equals(e.getValue())
                                || "true".equalsIgnoreCase(String.valueOf(e.getValue()))) {
                            modelScoreAvailable++;
                        }
                    }
                }
            }
        }
        List<AiDecisionStatsView.AdoptionModeBucket> byAdoption = adoptionCounts.entrySet().stream()
                .map(e -> new AiDecisionStatsView.AdoptionModeBucket(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.total(), a.total()))
                .toList();
        double scoreAvailRate = modelScoreCalls == 0
                ? 0.0 : (double) modelScoreAvailable / (double) modelScoreCalls;

        return new AiDecisionStatsView(
                total, success, failed, pending, timedOut, divergenceCount, rate, failRate,
                byType, byAdoption, modelScoreCalls, modelScoreAvailable, scoreAvailRate);
    }

    @Override
    public EngineDecisionStatsView queryEngineStats(Long startTimeMs, Long endTimeMs, String eventTypeCode) {
        LambdaQueryWrapper<EngineDecisionRecordPO> base = new LambdaQueryWrapper<>();
        if (startTimeMs != null) {
            base.ge(EngineDecisionRecordPO::getEventTime, toLocalDateTime(startTimeMs));
        }
        if (endTimeMs != null) {
            base.le(EngineDecisionRecordPO::getEventTime, toLocalDateTime(endTimeMs));
        }
        if (notBlank(eventTypeCode)) {
            base.eq(EngineDecisionRecordPO::getEventTypeCode, eventTypeCode);
        }
        long total = engineMapper.selectCount(base);
        List<EngineDecisionRecordPO> rows = engineMapper.selectList(
                base.select(
                        EngineDecisionRecordPO::getFinalDecision,
                        EngineDecisionRecordPO::getEventTypeCode,
                        EngineDecisionRecordPO::getElapsedMs)
                        .last("LIMIT 10000"));
        Map<String, Long> distribution = new LinkedHashMap<>();
        Map<String, Long> typeBuckets = new LinkedHashMap<>();
        List<Long> elapsed = new ArrayList<>();
        for (EngineDecisionRecordPO po : rows) {
            String decision = po.getFinalDecision() == null ? "UNKNOWN" : po.getFinalDecision();
            distribution.merge(decision, 1L, Long::sum);
            String code = po.getEventTypeCode() == null ? "" : po.getEventTypeCode();
            typeBuckets.merge(code, 1L, Long::sum);
            if (po.getElapsedMs() != null) {
                elapsed.add(po.getElapsedMs());
            }
        }
        double avg = 0.0;
        long p99 = 0L;
        if (!elapsed.isEmpty()) {
            long sum = 0L;
            for (Long ms : elapsed) {
                sum += ms;
            }
            avg = (double) sum / elapsed.size();
            elapsed.sort(Long::compareTo);
            int idx = (int) Math.ceil(elapsed.size() * 0.99) - 1;
            p99 = elapsed.get(Math.max(0, idx));
        }
        List<EngineDecisionStatsView.EventTypeBucket> byType = typeBuckets.entrySet().stream()
                .map(e -> new EngineDecisionStatsView.EventTypeBucket(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.total(), a.total()))
                .toList();
        return new EngineDecisionStatsView(total, distribution, avg, p99, byType);
    }

    private static boolean isTimeoutReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("timeout") || reason.contains("超时");
    }

    private EngineDecisionRecordPO findEnginePo(String eventId) {
        return engineMapper.selectOne(new LambdaQueryWrapper<EngineDecisionRecordPO>()
                .eq(EngineDecisionRecordPO::getEventId, eventId)
                .last("LIMIT 1"));
    }

    private AiDecisionRecordPO findAiPo(String eventId) {
        return aiMapper.selectOne(new LambdaQueryWrapper<AiDecisionRecordPO>()
                .eq(AiDecisionRecordPO::getEventId, eventId)
                .last("LIMIT 1"));
    }

    private EngineDecisionRecordView toEngineView(EngineDecisionRecordPO po) {
        String bizOrderId = po.getBusinessOrderId() != null ? po.getBusinessOrderId() : po.getEventId();
        return new EngineDecisionRecordView(
                po.getEventId(),
                po.getCorrelationId(),
                bizOrderId,
                po.getMerchantId(),
                po.getEventTypeCode(),
                toEpochMs(po.getEventTime()),
                po.getEngineDecision(),
                po.getFinalDecision(),
                po.getInvokeMode(),
                po.getRulePackageId(),
                po.getDecisionFlowId(),
                readMap(po.getDetailJson()),
                po.getElapsedMs(),
                toEpochMs(po.getCreatedAt()));
    }

    private AiDecisionRecordView toAiView(AiDecisionRecordPO po) {
        return new AiDecisionRecordView(
                po.getEventId(),
                po.getCorrelationId(),
                po.getMerchantId(),
                po.getEventTypeCode(),
                toEpochMs(po.getEventTime()),
                po.getStatus(),
                po.getAgentDecision(),
                po.getConfidence(),
                po.getReason(),
                po.getEngineDecision(),
                po.getDivergence(),
                readTrace(po.getTraceJson()),
                po.getFailReason(),
                toEpochMs(po.getCreatedAt()),
                po.getCompletedAt() == null ? null : toEpochMs(po.getCompletedAt()));
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> readTrace(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, TRACE_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static boolean isDivergent(String engineDecision, String agentDecision) {
        if (engineDecision == null || agentDecision == null) {
            return false;
        }
        return !engineDecision.equalsIgnoreCase(agentDecision);
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDateTime();
    }

    private static long toEpochMs(LocalDateTime time) {
        if (time == null) {
            return 0L;
        }
        return time.atZone(ZONE).toInstant().toEpochMilli();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 从 evt-{uuid32} 提取关联 UUID。 */
    public static String correlationIdFromEventId(String eventId) {
        if (eventId == null) {
            return "";
        }
        String prefix = "evt-";
        if (eventId.startsWith(prefix)) {
            return eventId.substring(prefix.length()).toLowerCase(Locale.ROOT);
        }
        return eventId;
    }
}
