package com.riskplatform.ruleconfig.infrastructure.indicator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorGroup;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorGroupRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class IndicatorGroupRepositoryImpl implements IndicatorGroupRepository {

    private final IndicatorGroupMapper groupMapper;

    public IndicatorGroupRepositoryImpl(IndicatorGroupMapper groupMapper) {
        this.groupMapper = groupMapper;
    }

    @Override
    public IndicatorGroup save(IndicatorGroup group) {
        IndicatorGroupPO po = toPO(group);
        groupMapper.insert(po);
        group.assignId(po.getId());
        return group;
    }

    @Override
    public IndicatorGroup update(IndicatorGroup group) {
        groupMapper.updateById(toPO(group));
        return group;
    }

    @Override
    public boolean deleteById(Long id) {
        return groupMapper.deleteById(id) > 0;
    }

    @Override
    public Optional<IndicatorGroup> findById(Long id) {
        return Optional.ofNullable(groupMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<IndicatorGroup> findAll() {
        return groupMapper.selectList(new LambdaQueryWrapper<IndicatorGroupPO>()
                        .orderByDesc(IndicatorGroupPO::getUpdatedAt))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByName(String name) {
        return groupMapper.exists(new LambdaQueryWrapper<IndicatorGroupPO>().eq(IndicatorGroupPO::getName, name));
    }

    @Override
    public boolean existsByNameExceptId(String name, Long id) {
        return groupMapper.exists(new LambdaQueryWrapper<IndicatorGroupPO>()
                .eq(IndicatorGroupPO::getName, name)
                .ne(IndicatorGroupPO::getId, id));
    }

    @Override
    public long countIndicatorsTotal(Long groupId) {
        return groupMapper.countAllIndicatorsByGroup(groupId);
    }

    @Override
    public long countIndicators(Long groupId, String status) {
        return groupMapper.countIndicatorsByGroup(groupId, status);
    }

    @Override
    public long countUngroupedIndicators(String status) {
        return groupMapper.countUngroupedIndicators(status);
    }

    private IndicatorGroupPO toPO(IndicatorGroup g) {
        IndicatorGroupPO po = new IndicatorGroupPO();
        po.setId(g.getId());
        po.setName(g.getName());
        po.setOrgName(g.getOrgName());
        po.setEventTypeCodes(g.getEventTypeCodes());
        po.setDescription(g.getDescription());
        return po;
    }

    private IndicatorGroup toDomain(IndicatorGroupPO po) {
        return IndicatorGroup.rehydrate(
                po.getId(), po.getName(), po.getOrgName(), po.getEventTypeCodes(), po.getDescription());
    }
}
