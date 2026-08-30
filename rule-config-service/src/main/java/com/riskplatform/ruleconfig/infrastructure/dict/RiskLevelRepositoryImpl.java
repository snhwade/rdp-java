package com.riskplatform.ruleconfig.infrastructure.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.dict.DictStatus;
import com.riskplatform.ruleconfig.domain.dict.RiskLevel;
import com.riskplatform.ruleconfig.domain.dict.RiskLevelRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RiskLevel 仓储 MyBatis-Plus 实现（R12.1）。列表按 order_no 升序。
 */
@Repository
public class RiskLevelRepositoryImpl implements RiskLevelRepository {

    private final RiskLevelMapper mapper;

    public RiskLevelRepositoryImpl(RiskLevelMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RiskLevel save(RiskLevel riskLevel) {
        RiskLevelPO po = toPO(riskLevel);
        mapper.insert(po);
        riskLevel.assignId(po.getId());
        return riskLevel;
    }

    @Override
    public void update(RiskLevel riskLevel) {
        mapper.updateById(toPO(riskLevel));
    }

    @Override
    public Optional<RiskLevel> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<RiskLevel> findByCode(String code) {
        RiskLevelPO po = mapper.selectOne(new LambdaQueryWrapper<RiskLevelPO>()
                .eq(RiskLevelPO::getCode, code));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return mapper.exists(new LambdaQueryWrapper<RiskLevelPO>().eq(RiskLevelPO::getCode, code));
    }

    @Override
    public List<RiskLevel> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<RiskLevelPO>()
                        .orderByAsc(RiskLevelPO::getOrderNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    private RiskLevelPO toPO(RiskLevel e) {
        RiskLevelPO po = new RiskLevelPO();
        po.setId(e.getId());
        po.setCode(e.getCode());
        po.setName(e.getName());
        po.setOrderNo(e.getOrderNo());
        po.setStatus(e.getStatus().name());
        return po;
    }

    private RiskLevel toDomain(RiskLevelPO po) {
        DictStatus status = "DISABLED".equals(po.getStatus()) ? DictStatus.DISABLED : DictStatus.ENABLED;
        int orderNo = po.getOrderNo() == null ? 0 : po.getOrderNo();
        return RiskLevel.rehydrate(po.getId(), po.getCode(), po.getName(), orderNo, status);
    }
}
