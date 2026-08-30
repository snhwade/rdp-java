package com.riskplatform.ruleconfig.infrastructure.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.dict.DecisionTag;
import com.riskplatform.ruleconfig.domain.dict.DecisionTagRepository;
import com.riskplatform.ruleconfig.domain.dict.DictStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * DecisionTag 仓储 MyBatis-Plus 实现（R12.1）。
 */
@Repository
public class DecisionTagRepositoryImpl implements DecisionTagRepository {

    private final DecisionTagMapper mapper;

    public DecisionTagRepositoryImpl(DecisionTagMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DecisionTag save(DecisionTag decisionTag) {
        DecisionTagPO po = toPO(decisionTag);
        mapper.insert(po);
        decisionTag.assignId(po.getId());
        return decisionTag;
    }

    @Override
    public void update(DecisionTag decisionTag) {
        mapper.updateById(toPO(decisionTag));
    }

    @Override
    public Optional<DecisionTag> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<DecisionTag> findByCode(String code) {
        DecisionTagPO po = mapper.selectOne(new LambdaQueryWrapper<DecisionTagPO>()
                .eq(DecisionTagPO::getCode, code));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return mapper.exists(new LambdaQueryWrapper<DecisionTagPO>().eq(DecisionTagPO::getCode, code));
    }

    @Override
    public List<DecisionTag> findAll() {
        return mapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    private DecisionTagPO toPO(DecisionTag e) {
        DecisionTagPO po = new DecisionTagPO();
        po.setId(e.getId());
        po.setCode(e.getCode());
        po.setName(e.getName());
        po.setApplicableAssetType(e.getApplicableAssetType());
        po.setStatus(e.getStatus().name());
        return po;
    }

    private DecisionTag toDomain(DecisionTagPO po) {
        DictStatus status = "DISABLED".equals(po.getStatus()) ? DictStatus.DISABLED : DictStatus.ENABLED;
        return DecisionTag.rehydrate(po.getId(), po.getCode(), po.getName(),
                po.getApplicableAssetType(), status);
    }
}
