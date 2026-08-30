package com.riskplatform.ruleconfig.domain.ratingmodel;

import java.util.List;
import java.util.Optional;

/**
 * 评级模型版本仓储端口（risk-console-redesign，R10.6/R11.5/R12.1/R13.1）。
 *
 * <p>由基础设施层用 MyBatis-Plus 持久化到 {@code rating_model_version} 表。每次评级模型
 * 创建/保存（新建版本）时写入一条不可变快照，供「版本历史」与「源码」页签消费。
 */
public interface RatingModelVersionRepository {

    /** 保存一条版本快照。 */
    RatingModelVersion save(RatingModelVersion version);

    /** 查询某评级模型当前最大版本号；无历史返回 0。 */
    int findMaxVersion(Long ratingModelId);

    /** 按评级模型查询版本列表（版本号降序）。 */
    List<RatingModelVersion> findByRatingModelId(Long ratingModelId);

    /** 查询某评级模型的指定版本。 */
    Optional<RatingModelVersion> findByRatingModelIdAndVersion(Long ratingModelId, int version);
}
