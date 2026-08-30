package com.riskplatform.ruleconfig.infrastructure.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.dict.DictStatus;
import com.riskplatform.ruleconfig.domain.dict.RiskType;
import com.riskplatform.ruleconfig.domain.dict.RiskTypeRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RiskType 仓储 MyBatis-Plus 实现（R12.1）。
 */
@Repository
public class RiskTypeRepositoryImpl implements RiskTypeRepository {

    private final RiskTypeMapper mapper;

    public RiskTypeRepositoryImpl(RiskTypeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RiskType save(RiskType riskType) {
        RiskTypePO po = toPO(riskType);
        mapper.insert(po);
        riskType.assignId(po.getId());
        return riskType;
    }

    @Override
    public void update(RiskType riskType) {
        mapper.updateById(toPO(riskType));
    }

    @Override
    public Optional<RiskType> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<RiskType> findByCode(String code) {
        RiskTypePO po = mapper.selectOne(new LambdaQueryWrapper<RiskTypePO>()
                .eq(RiskTypePO::getCode, code));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return mapper.exists(new LambdaQueryWrapper<RiskTypePO>().eq(RiskTypePO::getCode, code));
    }

    @Override
    public List<RiskType> findAll() {
        return mapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    private RiskTypePO toPO(RiskType e) {
        RiskTypePO po = new RiskTypePO();
        po.setId(e.getId());
        po.setCode(e.getCode());
        po.setName(e.getName());
        po.setStatus(e.getStatus().name());
        return po;
    }

    private RiskType toDomain(RiskTypePO po) {
        DictStatus status = "DISABLED".equals(po.getStatus()) ? DictStatus.DISABLED : DictStatus.ENABLED;
        return RiskType.rehydrate(po.getId(), po.getCode(), po.getName(), status);
    }
}
