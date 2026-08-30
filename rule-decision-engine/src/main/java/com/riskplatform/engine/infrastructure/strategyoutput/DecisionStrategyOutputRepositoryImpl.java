package com.riskplatform.engine.infrastructure.strategyoutput;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.domain.strategy.StrategyCategory;
import com.riskplatform.engine.domain.strategy.output.DecisionStrategyOutputRepository;
import com.riskplatform.engine.domain.strategy.output.DecisionStrategyRecord;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 决策产出策略记录仓储 MyBatis-Plus 实现（R3.4/R3.5）。
 *
 * <p>仅负责把策略记录落库到 decision_strategy_output 并提供查询，
 * <b>不调用任何外部系统</b>。payload 以 JSON 持久化到 payload_json。
 */
@Repository
public class DecisionStrategyOutputRepositoryImpl implements DecisionStrategyOutputRepository {

    private final DecisionStrategyOutputMapper mapper;
    private final ObjectMapper objectMapper;

    public DecisionStrategyOutputRepositoryImpl(DecisionStrategyOutputMapper mapper,
                                                ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveAll(List<DecisionStrategyRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (DecisionStrategyRecord record : records) {
            if (record == null) {
                continue;
            }
            DecisionStrategyOutputPO po = new DecisionStrategyOutputPO();
            po.setEventId(record.eventId());
            po.setDecisionId(record.decisionId());
            po.setRuleV2Id(record.ruleV2Id());
            po.setCategory(record.category() == null ? null : record.category().name());
            po.setStrategyCode(record.strategyCode());
            po.setPayloadJson(writePayload(record.payload()));
            // 显式设置创建时间，避免依赖未注册的 MetaObjectHandler 自动填充
            po.setCreatedAt(record.createdAt() == null ? LocalDateTime.now() : record.createdAt());
            mapper.insert(po);
        }
    }

    @Override
    public List<DecisionStrategyRecord> findByEventId(String eventId) {
        List<DecisionStrategyOutputPO> pos = mapper.selectList(
                new LambdaQueryWrapper<DecisionStrategyOutputPO>()
                        .eq(DecisionStrategyOutputPO::getEventId, eventId)
                        .orderByAsc(DecisionStrategyOutputPO::getId));
        return pos.stream().map(this::toDomain).toList();
    }

    @Override
    public List<DecisionStrategyRecord> findByDecisionId(Long decisionId) {
        List<DecisionStrategyOutputPO> pos = mapper.selectList(
                new LambdaQueryWrapper<DecisionStrategyOutputPO>()
                        .eq(DecisionStrategyOutputPO::getDecisionId, decisionId)
                        .orderByAsc(DecisionStrategyOutputPO::getId));
        return pos.stream().map(this::toDomain).toList();
    }

    private DecisionStrategyRecord toDomain(DecisionStrategyOutputPO po) {
        return new DecisionStrategyRecord(
                po.getId(),
                po.getEventId(),
                po.getDecisionId(),
                po.getRuleV2Id(),
                po.getCategory() == null ? null : StrategyCategory.valueOf(po.getCategory()),
                po.getStrategyCode(),
                readPayload(po.getPayloadJson()),
                po.getCreatedAt());
    }

    private String writePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> readPayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
