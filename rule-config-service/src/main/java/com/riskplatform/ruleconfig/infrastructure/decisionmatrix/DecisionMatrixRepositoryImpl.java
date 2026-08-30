package com.riskplatform.ruleconfig.infrastructure.decisionmatrix;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.decisionmatrix.DecisionMatrix;
import com.riskplatform.ruleconfig.domain.decisionmatrix.DecisionMatrixRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 决策矩阵仓储 MyBatis-Plus 实现（S9）。bins/cells 以 JSON 持久化。 */
@Repository
public class DecisionMatrixRepositoryImpl implements DecisionMatrixRepository {

    private final DecisionMatrixMapper mapper;
    private final ObjectMapper objectMapper;

    public DecisionMatrixRepositoryImpl(DecisionMatrixMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public DecisionMatrix save(DecisionMatrix matrix) {
        DecisionMatrixPO po = toPO(matrix);
        mapper.insert(po);
        matrix.assignId(po.getId());
        return matrix;
    }

    @Override
    public DecisionMatrix update(DecisionMatrix matrix) {
        mapper.updateById(toPO(matrix));
        return matrix;
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public Optional<DecisionMatrix> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<DecisionMatrix> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<>()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<DecisionMatrix> findByEventTypeCode(String eventTypeCode) {
        return mapper.selectList(new LambdaQueryWrapper<DecisionMatrixPO>()
                        .eq(DecisionMatrixPO::getEventTypeCode, eventTypeCode)
                        .eq(DecisionMatrixPO::getStatus, "ENABLED"))
                .stream().map(this::toDomain).toList();
    }

    private DecisionMatrixPO toPO(DecisionMatrix m) {
        DecisionMatrixPO po = new DecisionMatrixPO();
        po.setId(m.getId());
        po.setName(m.getName());
        po.setEventTypeCode(m.getEventTypeCode());
        po.setRowVar(m.getRowVar());
        po.setColVar(m.getColVar());
        po.setStatus(m.getStatus());
        po.setRowBinsJson(writeJson(m.getRowBins()));
        po.setColBinsJson(writeJson(m.getColBins()));
        po.setCellsJson(writeJson(m.getCells()));
        return po;
    }

    private DecisionMatrix toDomain(DecisionMatrixPO po) {
        List<DecisionMatrix.Bin> rowBins = readJson(po.getRowBinsJson(),
                new TypeReference<List<DecisionMatrix.Bin>>() {});
        List<DecisionMatrix.Bin> colBins = readJson(po.getColBinsJson(),
                new TypeReference<List<DecisionMatrix.Bin>>() {});
        List<DecisionMatrix.Cell> cells = readJson(po.getCellsJson(),
                new TypeReference<List<DecisionMatrix.Cell>>() {});
        return DecisionMatrix.rehydrate(po.getId(), po.getName(), po.getEventTypeCode(),
                po.getRowVar(), rowBins, po.getColVar(), colBins, cells, po.getStatus());
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("决策矩阵 JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("决策矩阵 JSON 反序列化失败: " + e.getMessage(), e);
        }
    }
}
