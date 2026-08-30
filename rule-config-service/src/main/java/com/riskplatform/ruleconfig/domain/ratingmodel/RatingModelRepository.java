package com.riskplatform.ruleconfig.domain.ratingmodel;

import java.util.List;
import java.util.Optional;

/**
 * 评级模型仓储端口（risk-console-redesign，R10）。
 *
 * <p>由基础设施层用 MyBatis-Plus 持久化到 {@code rating_model} 聚合根表，并级联维护其
 * 等级区间（{@code rating_grade_band}）与评级子项/定级项（{@code rating_item}）两张从表。
 * 保存/更新以聚合为单位：写入聚合根后整体重写其等级区间与子项集合，加载时一并回填。
 */
public interface RatingModelRepository {

    /** 保存新评级模型（含等级区间与子项），返回带 id 的聚合。 */
    RatingModel save(RatingModel model);

    /** 更新评级模型（含等级区间与子项整体重写）。 */
    RatingModel update(RatingModel model);

    /** 按 id 删除。 */
    boolean deleteById(Long id);

    /** 按 id 查询（回填等级区间与子项）。 */
    Optional<RatingModel> findById(Long id);

    /** 查询全部评级模型（卡片墙用，无则空列表）。 */
    List<RatingModel> findAll();

    /** 按所属事件查询评级模型（卡片墙筛选用，无则空列表）。 */
    List<RatingModel> findByEventTypeCode(String eventTypeCode);
}
