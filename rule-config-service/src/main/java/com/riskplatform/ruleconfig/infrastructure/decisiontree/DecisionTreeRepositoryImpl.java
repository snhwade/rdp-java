package com.riskplatform.ruleconfig.infrastructure.decisiontree;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.decisiontree.DecisionTree;
import com.riskplatform.ruleconfig.domain.decisiontree.DecisionTreeRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 决策树仓储 MyBatis-Plus 实现（S8）。nodes 以 JSON 持久化。 */
@Repository
public class DecisionTreeRepositoryImpl implements DecisionTreeRepository {

    private final DecisionTreeMapper mapper;
    private final ObjectMapper objectMapper;

    public DecisionTreeRepositoryImpl(DecisionTreeMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public DecisionTree save(DecisionTree tree) {
        DecisionTreePO po = toPO(tree);
        mapper.insert(po);
        tree.assignId(po.getId());
        return tree;
    }

    @Override
    public DecisionTree update(DecisionTree tree) {
        mapper.updateById(toPO(tree));
        return tree;
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public Optional<DecisionTree> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<DecisionTree> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<>()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<DecisionTree> findByEventTypeCode(String eventTypeCode) {
        return mapper.selectList(new LambdaQueryWrapper<DecisionTreePO>()
                        .eq(DecisionTreePO::getEventTypeCode, eventTypeCode)
                        .eq(DecisionTreePO::getStatus, "ENABLED"))
                .stream().map(this::toDomain).toList();
    }

    private DecisionTreePO toPO(DecisionTree t) {
        DecisionTreePO po = new DecisionTreePO();
        po.setId(t.getId());
        po.setName(t.getName());
        po.setEventTypeCode(t.getEventTypeCode());
        po.setRootNodeId(t.getRootNodeId());
        po.setStatus(t.getStatus());
        po.setNodesJson(writeJson(t.getNodes()));
        return po;
    }

    private DecisionTree toDomain(DecisionTreePO po) {
        List<DecisionTree.Node> nodes = readJson(po.getNodesJson(),
                new TypeReference<List<DecisionTree.Node>>() {});
        return DecisionTree.rehydrate(
                po.getId(), po.getName(), po.getEventTypeCode(), po.getRootNodeId(), nodes, po.getStatus());
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("决策树 JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("决策树 JSON 反序列化失败: " + e.getMessage(), e);
        }
    }
}
