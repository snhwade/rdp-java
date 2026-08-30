package com.riskplatform.ruleconfig.infrastructure.enums;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.enums.EnumDataType;
import com.riskplatform.ruleconfig.domain.enums.EnumLib;
import com.riskplatform.ruleconfig.domain.enums.EnumLibRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * EnumLib 仓储 MyBatis-Plus 实现（R12.2）。
 */
@Repository
public class EnumLibRepositoryImpl implements EnumLibRepository {

    private final EnumLibMapper mapper;

    public EnumLibRepositoryImpl(EnumLibMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public EnumLib save(EnumLib enumLib) {
        EnumLibPO po = toPO(enumLib);
        mapper.insert(po);
        enumLib.assignId(po.getId());
        return enumLib;
    }

    @Override
    public void update(EnumLib enumLib) {
        mapper.updateById(toPO(enumLib));
    }

    @Override
    public Optional<EnumLib> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<EnumLib> findByCode(String code) {
        EnumLibPO po = mapper.selectOne(new LambdaQueryWrapper<EnumLibPO>()
                .eq(EnumLibPO::getCode, code));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return mapper.exists(new LambdaQueryWrapper<EnumLibPO>().eq(EnumLibPO::getCode, code));
    }

    @Override
    public List<EnumLib> findAll() {
        return mapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    private EnumLibPO toPO(EnumLib e) {
        EnumLibPO po = new EnumLibPO();
        po.setId(e.getId());
        po.setCode(e.getCode());
        po.setName(e.getName());
        po.setDataType(e.getDataType().name());
        po.setStatus(e.getStatus().name());
        return po;
    }

    private EnumLib toDomain(EnumLibPO po) {
        EnumLib.EnumStatus status = "DISABLED".equals(po.getStatus())
                ? EnumLib.EnumStatus.DISABLED : EnumLib.EnumStatus.ENABLED;
        EnumDataType dataType = parseDataType(po.getDataType());
        return EnumLib.rehydrate(po.getId(), po.getCode(), po.getName(), dataType, status);
    }

    private EnumDataType parseDataType(String raw) {
        if (raw == null) {
            return EnumDataType.STRING;
        }
        try {
            return EnumDataType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return EnumDataType.STRING;
        }
    }
}
