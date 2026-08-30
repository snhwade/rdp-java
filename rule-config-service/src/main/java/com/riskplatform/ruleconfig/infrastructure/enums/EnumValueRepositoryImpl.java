package com.riskplatform.ruleconfig.infrastructure.enums;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.enums.EnumValue;
import com.riskplatform.ruleconfig.domain.enums.EnumValueRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * EnumValue 仓储 MyBatis-Plus 实现（R12.2）。列表按 order_no 升序。
 */
@Repository
public class EnumValueRepositoryImpl implements EnumValueRepository {

    private final EnumValueMapper mapper;

    public EnumValueRepositoryImpl(EnumValueMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public EnumValue save(EnumValue enumValue) {
        EnumValuePO po = toPO(enumValue);
        mapper.insert(po);
        enumValue.assignId(po.getId());
        return enumValue;
    }

    @Override
    public void update(EnumValue enumValue) {
        mapper.updateById(toPO(enumValue));
    }

    @Override
    public Optional<EnumValue> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<EnumValue> findByLibAndValue(Long enumLibId, String value) {
        EnumValuePO po = mapper.selectOne(new LambdaQueryWrapper<EnumValuePO>()
                .eq(EnumValuePO::getEnumLibId, enumLibId)
                .eq(EnumValuePO::getValue, value));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public boolean existsByLibAndValue(Long enumLibId, String value) {
        return mapper.exists(new LambdaQueryWrapper<EnumValuePO>()
                .eq(EnumValuePO::getEnumLibId, enumLibId)
                .eq(EnumValuePO::getValue, value));
    }

    @Override
    public List<EnumValue> findByLibId(Long enumLibId) {
        return mapper.selectList(new LambdaQueryWrapper<EnumValuePO>()
                        .eq(EnumValuePO::getEnumLibId, enumLibId)
                        .orderByAsc(EnumValuePO::getOrderNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public void deleteByLibId(Long enumLibId) {
        mapper.delete(new LambdaQueryWrapper<EnumValuePO>().eq(EnumValuePO::getEnumLibId, enumLibId));
    }

    private EnumValuePO toPO(EnumValue e) {
        EnumValuePO po = new EnumValuePO();
        po.setId(e.getId());
        po.setEnumLibId(e.getEnumLibId());
        po.setValue(e.getValue());
        po.setLabel(e.getLabel());
        po.setOrderNo(e.getOrderNo());
        return po;
    }

    private EnumValue toDomain(EnumValuePO po) {
        int orderNo = po.getOrderNo() == null ? 0 : po.getOrderNo();
        return EnumValue.rehydrate(po.getId(), po.getEnumLibId(), po.getValue(), po.getLabel(), orderNo);
    }
}
