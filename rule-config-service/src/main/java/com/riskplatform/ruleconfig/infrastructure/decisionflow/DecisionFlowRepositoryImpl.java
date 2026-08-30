package com.riskplatform.ruleconfig.infrastructure.decisionflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlow;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 决策流仓储 MyBatis-Plus 实现（S4）。nodes/edges 以 JSON 持久化。 */
@Repository
public class DecisionFlowRepositoryImpl implements DecisionFlowRepository {

    private final DecisionFlowMapper mapper;
    private final ObjectMapper objectMapper;

    public DecisionFlowRepositoryImpl(DecisionFlowMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public DecisionFlow save(DecisionFlow flow) {
        DecisionFlowPO po = toPO(flow);
        mapper.insert(po);
        flow.assignId(po.getId());
        return flow;
    }

    @Override
    public DecisionFlow update(DecisionFlow flow) {
        mapper.updateById(toPO(flow));
        return flow;
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public Optional<DecisionFlow> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<DecisionFlow> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<>()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<DecisionFlow> findByEventTypeCode(String eventTypeCode) {
        return mapper.selectList(new LambdaQueryWrapper<DecisionFlowPO>()
                        .eq(DecisionFlowPO::getEventTypeCode, eventTypeCode)
                        .eq(DecisionFlowPO::getStatus, "ENABLED"))
                .stream().map(this::toDomain).toList();
    }

    private DecisionFlowPO toPO(DecisionFlow f) {
        DecisionFlowPO po = new DecisionFlowPO();
        po.setId(f.getId());
        po.setName(f.getName());
        po.setEventTypeCode(f.getEventTypeCode());
        po.setStartNodeId(f.getStartNodeId());
        po.setStatus(f.getStatus());
        po.setRemark(f.getRemark());
        po.setPrevOnlineVersion(f.getPrevOnlineVersion());
        po.setNodesJson(writeJson(f.getNodes()));
        po.setEdgesJson(writeJson(f.getEdges()));
        po.setScenarioIdsJson(f.getScenarioIds() == null ? null : writeJson(f.getScenarioIds()));
        po.setEventCodesJson(f.getEventCodes() == null ? null : writeJson(f.getEventCodes()));
        po.setApplicableOrgId(f.getApplicableOrgId());
        po.setIncludeSubOrg(f.isIncludeSubOrg());
        return po;
    }

    private DecisionFlow toDomain(DecisionFlowPO po) {
        List<DecisionFlow.Node> nodes = readJson(po.getNodesJson(),
                new TypeReference<List<DecisionFlow.Node>>() {});
        List<DecisionFlow.Edge> edges = readJson(po.getEdgesJson(),
                new TypeReference<List<DecisionFlow.Edge>>() {});
        // 注意：仅基础 validate（兼容历史数据），不做保存期结构校验。
        DecisionFlow f = DecisionFlow.create(po.getName(), po.getEventTypeCode(),
                nodes, edges, po.getStartNodeId());
        f.assignId(po.getId());
        f.assignRemark(po.getRemark());
        f.assignPrevOnlineVersion(po.getPrevOnlineVersion());
        List<Long> scenarioIds = po.getScenarioIdsJson() == null ? null
                : readJson(po.getScenarioIdsJson(), new TypeReference<List<Long>>() {});
        List<String> eventCodes = po.getEventCodesJson() == null ? null
                : readJson(po.getEventCodesJson(), new TypeReference<List<String>>() {});
        f.assignScope(scenarioIds, eventCodes, po.getApplicableOrgId(),
                Boolean.TRUE.equals(po.getIncludeSubOrg()));
        return f;
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("决策流 JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("决策流 JSON 反序列化失败: " + e.getMessage(), e);
        }
    }
}
