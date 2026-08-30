package com.riskplatform.ruleconfig.infrastructure.ratingmodel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModelVersion;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModelVersionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 评级模型版本仓储 MyBatis-Plus 实现（risk-console-redesign，R10.6）。 */
@Repository
public class RatingModelVersionRepositoryImpl implements RatingModelVersionRepository {

    private final RatingModelVersionMapper mapper;

    public RatingModelVersionRepositoryImpl(RatingModelVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RatingModelVersion save(RatingModelVersion version) {
        RatingModelVersionPO po = new RatingModelVersionPO();
        po.setRatingModelId(version.getRatingModelId());
        po.setVersion(version.getVersion());
        po.setSnapshotJson(version.getSnapshotJson());
        po.setCreatedBy(version.getCreatedBy());
        mapper.insert(po);
        version.assignId(po.getId());
        version.assignCreatedAt(po.getCreatedAt());
        return version;
    }

    @Override
    public int findMaxVersion(Long ratingModelId) {
        RatingModelVersionPO latest = mapper.selectList(new LambdaQueryWrapper<RatingModelVersionPO>()
                        .eq(RatingModelVersionPO::getRatingModelId, ratingModelId)
                        .orderByDesc(RatingModelVersionPO::getVersion))
                .stream().findFirst().orElse(null);
        return latest == null || latest.getVersion() == null ? 0 : latest.getVersion();
    }

    @Override
    public List<RatingModelVersion> findByRatingModelId(Long ratingModelId) {
        return mapper.selectList(new LambdaQueryWrapper<RatingModelVersionPO>()
                        .eq(RatingModelVersionPO::getRatingModelId, ratingModelId)
                        .orderByDesc(RatingModelVersionPO::getVersion))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<RatingModelVersion> findByRatingModelIdAndVersion(Long ratingModelId, int version) {
        RatingModelVersionPO po = mapper.selectList(new LambdaQueryWrapper<RatingModelVersionPO>()
                        .eq(RatingModelVersionPO::getRatingModelId, ratingModelId)
                        .eq(RatingModelVersionPO::getVersion, version))
                .stream().findFirst().orElse(null);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    private RatingModelVersion toDomain(RatingModelVersionPO po) {
        return RatingModelVersion.rehydrate(po.getId(), po.getRatingModelId(), po.getVersion(),
                po.getSnapshotJson(), po.getCreatedBy(), po.getCreatedAt());
    }
}
