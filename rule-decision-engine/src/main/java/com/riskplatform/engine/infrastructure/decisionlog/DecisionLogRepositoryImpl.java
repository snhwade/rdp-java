package com.riskplatform.engine.infrastructure.decisionlog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.domain.decision.Decision;
import com.riskplatform.engine.domain.decision.DecisionLog;
import com.riskplatform.engine.domain.decision.DecisionLogRepository;
import com.riskplatform.engine.domain.rule.GroupExecutionStatus;
import com.riskplatform.engine.domain.rule.HitDecision;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 决策日志仓储 MyBatis-Plus 实现（R15.1/R15.3）。
 * 命中规则列表以 JSON 持久化到 decision_log.hit_rules。
 */
@Repository
public class DecisionLogRepositoryImpl implements DecisionLogRepository {

    private final DecisionLogMapper mapper;
    private final ObjectMapper objectMapper;

    public DecisionLogRepositoryImpl(DecisionLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(DecisionLog log) {
        DecisionLogPO po = new DecisionLogPO();
        po.setEventId(log.eventId());
        po.setFinalDecision(log.finalDecision() == null ? null : log.finalDecision().name());
        po.setHitRules(writeHits(log.hitDecisions()));
        po.setElapsedMs((int) log.elapsedMs());
        po.setTimeoutReason(log.timeoutReason());
        po.setGroupStatus(log.groupStatus() == null ? null : log.groupStatus().name());
        // 显式设置创建时间，避免依赖未注册的 MetaObjectHandler 自动填充导致 created_at 为 null
        po.setCreatedAt(java.time.LocalDateTime.now());
        mapper.insert(po);
    }

    @Override
    public Optional<DecisionLog> findByEventId(String eventId) {
        DecisionLogPO po = mapper.selectOne(new LambdaQueryWrapper<DecisionLogPO>()
                .eq(DecisionLogPO::getEventId, eventId)
                .orderByDesc(DecisionLogPO::getId)
                .last("LIMIT 1"));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    private DecisionLog toDomain(DecisionLogPO po) {
        return new DecisionLog(
                po.getEventId(),
                po.getFinalDecision() == null ? null : Decision.valueOf(po.getFinalDecision()),
                readHits(po.getHitRules()),
                po.getElapsedMs() == null ? 0L : po.getElapsedMs(),
                po.getTimeoutReason(),
                po.getGroupStatus() == null ? null : GroupExecutionStatus.valueOf(po.getGroupStatus()));
    }

    private String writeHits(List<HitDecision> hits) {
        try {
            return objectMapper.writeValueAsString(hits == null ? List.of() : hits);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<HitDecision> readHits(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<HitDecision>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
