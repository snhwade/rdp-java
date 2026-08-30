package com.riskplatform.ruleconfig.infrastructure.indicator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.indicator.CombineMode;
import com.riskplatform.ruleconfig.domain.indicator.LogicalIndicator;
import com.riskplatform.ruleconfig.domain.indicator.LogicalIndicatorMember;
import com.riskplatform.ruleconfig.domain.indicator.LogicalIndicatorRepository;
import com.riskplatform.ruleconfig.domain.indicator.SliceGranularity;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class LogicalIndicatorRepositoryImpl implements LogicalIndicatorRepository {

    private final LogicalIndicatorMapper mapper;
    private final LogicalIndicatorMemberMapper memberMapper;

    public LogicalIndicatorRepositoryImpl(LogicalIndicatorMapper mapper,
                                          LogicalIndicatorMemberMapper memberMapper) {
        this.mapper = mapper;
        this.memberMapper = memberMapper;
    }

    @Override
    public LogicalIndicator save(LogicalIndicator indicator, List<LogicalIndicatorMember> members) {
        LogicalIndicatorPO po = toPO(indicator);
        mapper.insert(po);
        indicator.assignId(po.getId());
        replaceMembers(po.getId(), members);
        return loadById(po.getId()).orElseThrow();
    }

    @Override
    public LogicalIndicator update(LogicalIndicator indicator, List<LogicalIndicatorMember> members) {
        mapper.updateById(toPO(indicator));
        replaceMembers(indicator.getId(), members);
        return loadById(indicator.getId()).orElseThrow();
    }

    @Override
    public boolean deleteById(Long id) {
        memberMapper.delete(new LambdaQueryWrapper<LogicalIndicatorMemberPO>()
                .eq(LogicalIndicatorMemberPO::getLogicalId, id));
        return mapper.deleteById(id) > 0;
    }

    @Override
    public Optional<LogicalIndicator> findById(Long id) {
        return loadById(id);
    }

    @Override
    public Optional<LogicalIndicator> findByRefName(String refName) {
        LogicalIndicatorPO po = mapper.selectOne(new LambdaQueryWrapper<LogicalIndicatorPO>()
                .eq(LogicalIndicatorPO::getRefName, refName));
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po, loadMembers(po.getId())));
    }

    @Override
    public List<LogicalIndicator> findAll(Long groupId, Boolean ungroupedOnly, String status) {
        LambdaQueryWrapper<LogicalIndicatorPO> q = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(ungroupedOnly)) {
            q.isNull(LogicalIndicatorPO::getGroupId);
        } else if (groupId != null) {
            q.eq(LogicalIndicatorPO::getGroupId, groupId);
        }
        if (status != null && !status.isBlank()) {
            q.eq(LogicalIndicatorPO::getStatus, status);
        }
        return mapper.selectList(q).stream()
                .map(po -> toDomain(po, loadMembers(po.getId())))
                .toList();
    }

    @Override
    public boolean existsByRefName(String refName) {
        return mapper.exists(new LambdaQueryWrapper<LogicalIndicatorPO>()
                .eq(LogicalIndicatorPO::getRefName, refName));
    }

    private Optional<LogicalIndicator> loadById(Long id) {
        LogicalIndicatorPO po = mapper.selectById(id);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po, loadMembers(id)));
    }

    private List<LogicalIndicatorMember> loadMembers(Long logicalId) {
        return memberMapper.selectList(new LambdaQueryWrapper<LogicalIndicatorMemberPO>()
                        .eq(LogicalIndicatorMemberPO::getLogicalId, logicalId))
                .stream()
                .sorted(Comparator.comparingInt(m -> m.getSortOrder() == null ? 0 : m.getSortOrder()))
                .map(m -> new LogicalIndicatorMember(
                        m.getMemberRefName(), m.getEventTypeCode(),
                        m.getSortOrder() == null ? 0 : m.getSortOrder()))
                .toList();
    }

    private void replaceMembers(Long logicalId, List<LogicalIndicatorMember> members) {
        memberMapper.delete(new LambdaQueryWrapper<LogicalIndicatorMemberPO>()
                .eq(LogicalIndicatorMemberPO::getLogicalId, logicalId));
        int order = 0;
        for (LogicalIndicatorMember m : members) {
            LogicalIndicatorMemberPO po = new LogicalIndicatorMemberPO();
            po.setLogicalId(logicalId);
            po.setMemberRefName(m.memberRefName());
            po.setEventTypeCode(m.eventTypeCode());
            po.setSortOrder(m.sortOrder() > 0 ? m.sortOrder() : order++);
            memberMapper.insert(po);
        }
    }

    private LogicalIndicatorPO toPO(LogicalIndicator d) {
        LogicalIndicatorPO po = new LogicalIndicatorPO();
        po.setId(d.getId());
        po.setGroupId(d.getGroupId());
        po.setRefName(d.getRefName());
        po.setName(d.getName());
        po.setDescription(d.getDescription());
        po.setCombineMode(d.getCombineMode().name());
        po.setCombineExpression(d.getCombineExpression());
        po.setDimensions(d.getDimensions());
        po.setWindowDays(d.getWindowDays());
        po.setSliceGranularity(d.getSliceGranularity().name());
        po.setDefaultValueStrategy(d.getDefaultValueStrategy());
        po.setStatus(d.getStatus());
        return po;
    }

    private LogicalIndicator toDomain(LogicalIndicatorPO po, List<LogicalIndicatorMember> members) {
        return LogicalIndicator.rehydrate(
                po.getId(), po.getGroupId(), po.getRefName(), po.getName(), po.getDescription(),
                CombineMode.valueOf(po.getCombineMode()),
                po.getCombineExpression(),
                po.getDimensions(),
                po.getWindowDays() == null ? 1 : po.getWindowDays(),
                SliceGranularity.valueOf(po.getSliceGranularity()),
                po.getDefaultValueStrategy(),
                po.getStatus(),
                members);
    }
}
