package com.riskplatform.rating.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riskplatform.common.model.PagedResult;
import com.riskplatform.rating.domain.MerchantRating;
import com.riskplatform.rating.domain.MerchantRatingListView;
import com.riskplatform.rating.domain.MerchantRatingQuery;
import com.riskplatform.rating.domain.MerchantRatingRepository;
import com.riskplatform.rating.domain.RiskLevel;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** 商户评级仓储 MyBatis-Plus 实现（R12）。 */
@Repository
public class MerchantRatingRepositoryImpl implements MerchantRatingRepository {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final MerchantRatingMapper mapper;

    public MerchantRatingRepositoryImpl(MerchantRatingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(MerchantRating rating) {
        MerchantRatingPO po = new MerchantRatingPO();
        po.setMerchantId(rating.getMerchantId());
        po.setScore(rating.getScore());
        po.setLevel(rating.getLevel() == null ? null : rating.getLevel().name());
        po.setStatus(rating.getStatus().name());
        po.setFactors(rating.getFactors());
        if (mapper.selectById(rating.getMerchantId()) != null) {
            mapper.updateById(po);
        } else {
            mapper.insert(po);
        }
    }

    @Override
    public Optional<MerchantRating> findByMerchantId(String merchantId) {
        MerchantRatingPO po = mapper.selectById(merchantId);
        if (po == null) {
            return Optional.empty();
        }
        if (po.getScore() == null) {
            return Optional.of(MerchantRating.unrated(po.getMerchantId()));
        }
        MerchantRating rating = MerchantRating.rated(po.getMerchantId(), po.getScore(),
                po.getFactors() == null ? java.util.Map.of() : po.getFactors());
        // 确认等级一致（以分数映射为准）
        RiskLevel.fromScore(po.getScore());
        return Optional.of(rating);
    }

    @Override
    public PagedResult<MerchantRatingListView> query(MerchantRatingQuery query) {
        LambdaQueryWrapper<MerchantRatingPO> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(query.merchantId())) {
            wrapper.like(MerchantRatingPO::getMerchantId, query.merchantId());
        }
        if (notBlank(query.status())) {
            wrapper.eq(MerchantRatingPO::getStatus, query.status());
        }
        if (notBlank(query.level())) {
            wrapper.eq(MerchantRatingPO::getLevel, query.level());
        }
        if (query.startTimeMs() != null) {
            wrapper.ge(MerchantRatingPO::getUpdatedAt, toLocalDateTime(query.startTimeMs()));
        }
        if (query.endTimeMs() != null) {
            wrapper.le(MerchantRatingPO::getUpdatedAt, toLocalDateTime(query.endTimeMs()));
        }
        wrapper.orderByDesc(MerchantRatingPO::getUpdatedAt);

        Page<MerchantRatingPO> page = Page.of(query.page(), query.pageSize());
        IPage<MerchantRatingPO> result = mapper.selectPage(page, wrapper);
        List<MerchantRatingListView> views = result.getRecords().stream().map(this::toListView).toList();
        return PagedResult.of(views, query.page(), query.pageSize(), result.getTotal());
    }

    private MerchantRatingListView toListView(MerchantRatingPO po) {
        return new MerchantRatingListView(
                po.getMerchantId(),
                po.getScore(),
                po.getLevel(),
                po.getStatus(),
                po.getUpdatedAt());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZONE);
    }
}
