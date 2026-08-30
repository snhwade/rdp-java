package com.riskplatform.ruleconfig.infrastructure.indicator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinition;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionRepository;
import com.riskplatform.ruleconfig.domain.indicator.SliceGranularity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 指标定义仓储 MyBatis-Plus 实现（R7）。 */
@Repository
public class IndicatorDefinitionRepositoryImpl implements IndicatorDefinitionRepository {

    private final IndicatorMapper mapper;

    public IndicatorDefinitionRepositoryImpl(IndicatorMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public IndicatorDefinition save(IndicatorDefinition definition) {
        IndicatorPO po = toPO(definition);
        mapper.insert(po);
        definition.assignId(po.getId());
        return definition;
    }

    @Override
    public IndicatorDefinition update(IndicatorDefinition definition) {
        IndicatorPO po = toPO(definition);
        mapper.updateById(po);
        return definition;
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public Optional<IndicatorDefinition> findByRefName(String refName) {
        IndicatorPO po = mapper.selectOne(new LambdaQueryWrapper<IndicatorPO>()
                .eq(IndicatorPO::getRefName, refName));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<IndicatorDefinition> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<IndicatorDefinition> findAll(Long groupId, Boolean ungroupedOnly,
                                             String eventTypeCode, String status) {
        LambdaQueryWrapper<IndicatorPO> q = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(ungroupedOnly)) {
            q.isNull(IndicatorPO::getGroupId);
        } else if (groupId != null) {
            q.eq(IndicatorPO::getGroupId, groupId);
        }
        if (status != null && !status.isBlank()) {
            q.eq(IndicatorPO::getStatus, status);
        }
        return mapper.selectList(q).stream()
                .map(this::toDomain)
                .filter(d -> matchesEvent(d, eventTypeCode))
                .toList();
    }

    private static boolean matchesEvent(IndicatorDefinition d, String eventTypeCode) {
        if (eventTypeCode == null || eventTypeCode.isBlank()) {
            return true;
        }
        return d.getEventTypeCodes().contains(eventTypeCode);
    }

    @Override
    public boolean existsByRefName(String refName) {
        return mapper.exists(new LambdaQueryWrapper<IndicatorPO>().eq(IndicatorPO::getRefName, refName));
    }

    @Override
    public List<Long> findReferencingRuleIds(String refName) {
        return mapper.findReferencingRuleIds(refName);
    }

    private IndicatorPO toPO(IndicatorDefinition d) {
        IndicatorPO po = new IndicatorPO();
        po.setId(d.getId());
        po.setGroupId(d.getGroupId());
        po.setRefName(d.getRefName());
        po.setName(d.getName());
        po.setDescription(d.getDescription());
        po.setEventTypeCodes(d.getEventTypeCodes());
        po.setDimensions(d.getDimensions());
        po.setWindowDays(d.getWindowDays());
        po.setSliceGranularity(d.getSliceGranularity().name());
        po.setAccScript(d.getAccScript());
        po.setDefaultValueStrategy(d.getDefaultValueStrategy());
        po.setStatus(d.getStatus());
        po.setTemplateType(d.getTemplateType());
        po.setTemplateConfig(d.getTemplateConfig() == null ? null : new java.util.LinkedHashMap<>(d.getTemplateConfig()));
        return po;
    }

    private IndicatorDefinition toDomain(IndicatorPO po) {
        return IndicatorDefinition.rehydrate(
                po.getId(), po.getGroupId(), po.getRefName(), po.getName(), po.getDescription(),
                po.getEventTypeCodes(), po.getDimensions(),
                po.getWindowDays() == null ? 1 : po.getWindowDays(),
                SliceGranularity.valueOf(po.getSliceGranularity()),
                po.getAccScript(), po.getDefaultValueStrategy(), po.getStatus(),
                po.getTemplateType(), po.getTemplateConfig());
    }
}
