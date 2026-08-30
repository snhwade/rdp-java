package com.riskplatform.ruleconfig.infrastructure.decisiontable;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.decisiontable.DecisionTable;
import com.riskplatform.ruleconfig.domain.decisiontable.DecisionTableRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 决策表仓储 MyBatis-Plus 实现（S2）。columns/rows 以 JSON 字符串持久化。 */
@Repository
public class DecisionTableRepositoryImpl implements DecisionTableRepository {

    private final DecisionTableMapper mapper;
    private final ObjectMapper objectMapper;

    public DecisionTableRepositoryImpl(DecisionTableMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public DecisionTable save(DecisionTable table) {
        DecisionTablePO po = toPO(table);
        mapper.insert(po);
        table.assignId(po.getId());
        return table;
    }

    @Override
    public DecisionTable update(DecisionTable table) {
        mapper.updateById(toPO(table));
        return table;
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public Optional<DecisionTable> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<DecisionTable> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<>()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<DecisionTable> findByEventTypeCode(String eventTypeCode) {
        return mapper.selectList(new LambdaQueryWrapper<DecisionTablePO>()
                        .eq(DecisionTablePO::getEventTypeCode, eventTypeCode)
                        .eq(DecisionTablePO::getStatus, "ENABLED"))
                .stream().map(this::toDomain).toList();
    }

    private DecisionTablePO toPO(DecisionTable t) {
        DecisionTablePO po = new DecisionTablePO();
        po.setId(t.getId());
        po.setName(t.getName());
        po.setEventTypeCode(t.getEventTypeCode());
        po.setHitPolicy(t.getHitPolicy().name());
        po.setStatus(t.getStatus());
        po.setColumnsJson(writeJson(t.getColumns()));
        po.setRowsJson(writeJson(t.getRows()));
        return po;
    }

    private DecisionTable toDomain(DecisionTablePO po) {
        List<DecisionTable.Column> columns = readJson(po.getColumnsJson(),
                new TypeReference<List<DecisionTable.Column>>() {});
        List<DecisionTable.Row> rows = readJson(po.getRowsJson(),
                new TypeReference<List<DecisionTable.Row>>() {});
        return DecisionTable.rehydrate(
                po.getId(), po.getName(), po.getEventTypeCode(),
                DecisionTable.HitPolicy.valueOf(po.getHitPolicy()), columns, rows, po.getStatus());
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("决策表 JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("决策表 JSON 反序列化失败: " + e.getMessage(), e);
        }
    }
}
