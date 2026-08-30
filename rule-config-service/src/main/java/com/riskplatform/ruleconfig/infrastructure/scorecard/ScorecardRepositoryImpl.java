package com.riskplatform.ruleconfig.infrastructure.scorecard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.scorecard.Scorecard;
import com.riskplatform.ruleconfig.domain.scorecard.ScorecardRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 评分卡仓储 MyBatis-Plus 实现（S3）。variables/levels 以 JSON 持久化。 */
@Repository
public class ScorecardRepositoryImpl implements ScorecardRepository {

    private final ScorecardMapper mapper;
    private final ObjectMapper objectMapper;

    public ScorecardRepositoryImpl(ScorecardMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Scorecard save(Scorecard scorecard) {
        ScorecardPO po = toPO(scorecard);
        mapper.insert(po);
        scorecard.assignId(po.getId());
        return scorecard;
    }

    @Override
    public Scorecard update(Scorecard scorecard) {
        mapper.updateById(toPO(scorecard));
        return scorecard;
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public Optional<Scorecard> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Scorecard> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<>()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Scorecard> findByEventTypeCode(String eventTypeCode) {
        return mapper.selectList(new LambdaQueryWrapper<ScorecardPO>()
                        .eq(ScorecardPO::getEventTypeCode, eventTypeCode)
                        .eq(ScorecardPO::getStatus, "ENABLED"))
                .stream().map(this::toDomain).toList();
    }

    private ScorecardPO toPO(Scorecard s) {
        ScorecardPO po = new ScorecardPO();
        po.setId(s.getId());
        po.setName(s.getName());
        po.setEventTypeCode(s.getEventTypeCode());
        po.setStatus(s.getStatus());
        po.setVariablesJson(writeJson(s.getVariables()));
        po.setLevelsJson(writeJson(s.getLevels()));
        return po;
    }

    private Scorecard toDomain(ScorecardPO po) {
        List<Scorecard.Variable> variables = readJson(po.getVariablesJson(),
                new TypeReference<List<Scorecard.Variable>>() {});
        List<Scorecard.Level> levels = readJson(po.getLevelsJson(),
                new TypeReference<List<Scorecard.Level>>() {});
        return Scorecard.rehydrate(po.getId(), po.getName(), po.getEventTypeCode(), variables, levels, po.getStatus());
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("评分卡 JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("评分卡 JSON 反序列化失败: " + e.getMessage(), e);
        }
    }
}
