package com.riskplatform.ruleconfig.infrastructure.ratingmodel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModel;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModelRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 评级模型仓储 MyBatis-Plus 实现（risk-console-redesign，R10）。
 *
 * <p>以聚合为单位持久化：写入/更新 {@code rating_model} 聚合根后，整体重写其
 * 等级区间（{@code rating_grade_band}）与评级子项/定级项（{@code rating_item}）两张从表；
 * 加载时按 {@code rating_model_id} 回填两张从表并重建聚合。
 */
@Repository
public class RatingModelRepositoryImpl implements RatingModelRepository {

    private final RatingModelMapper modelMapper;
    private final RatingGradeBandMapper gradeBandMapper;
    private final RatingItemMapper itemMapper;

    public RatingModelRepositoryImpl(RatingModelMapper modelMapper,
                                     RatingGradeBandMapper gradeBandMapper,
                                     RatingItemMapper itemMapper) {
        this.modelMapper = modelMapper;
        this.gradeBandMapper = gradeBandMapper;
        this.itemMapper = itemMapper;
    }

    @Override
    public RatingModel save(RatingModel model) {
        RatingModelPO po = toPO(model);
        modelMapper.insert(po);
        model.assignId(po.getId());
        replaceChildren(model);
        return model;
    }

    @Override
    public RatingModel update(RatingModel model) {
        modelMapper.updateById(toPO(model));
        replaceChildren(model);
        return model;
    }

    @Override
    public boolean deleteById(Long id) {
        deleteChildren(id);
        return modelMapper.deleteById(id) > 0;
    }

    @Override
    public Optional<RatingModel> findById(Long id) {
        return Optional.ofNullable(modelMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<RatingModel> findAll() {
        return modelMapper.selectList(new LambdaQueryWrapper<>()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<RatingModel> findByEventTypeCode(String eventTypeCode) {
        return modelMapper.selectList(new LambdaQueryWrapper<RatingModelPO>()
                        .eq(RatingModelPO::getEventTypeCode, eventTypeCode))
                .stream().map(this::toDomain).toList();
    }

    /** 整体重写聚合从表：先删旧的等级区间与子项，再按当前聚合内容重插。 */
    private void replaceChildren(RatingModel model) {
        deleteChildren(model.getId());
        if (model.getGradeBands() != null) {
            for (RatingModel.GradeBand b : model.getGradeBands()) {
                RatingGradeBandPO po = new RatingGradeBandPO();
                po.setRatingModelId(model.getId());
                po.setMinScore(b.minScore());
                po.setMaxScore(b.maxScore());
                po.setGrade(b.grade());
                po.setOrderNo(b.orderNo());
                gradeBandMapper.insert(po);
            }
        }
        if (model.getItems() != null) {
            for (RatingModel.RatingItem item : model.getItems()) {
                RatingItemPO po = new RatingItemPO();
                po.setRatingModelId(model.getId());
                po.setCategory(item.category());
                po.setSubItem(item.subItem());
                po.setConditionExpr(item.condition());
                po.setScore(item.score());
                po.setSubItemCap(item.subItemCap());
                po.setImportance(item.importance());
                po.setGrade(item.grade());
                itemMapper.insert(po);
            }
        }
    }

    private void deleteChildren(Long ratingModelId) {
        if (ratingModelId == null) {
            return;
        }
        gradeBandMapper.delete(new LambdaQueryWrapper<RatingGradeBandPO>()
                .eq(RatingGradeBandPO::getRatingModelId, ratingModelId));
        itemMapper.delete(new LambdaQueryWrapper<RatingItemPO>()
                .eq(RatingItemPO::getRatingModelId, ratingModelId));
    }

    private RatingModelPO toPO(RatingModel m) {
        RatingModelPO po = new RatingModelPO();
        po.setId(m.getId());
        po.setName(m.getName());
        po.setEventTypeCode(m.getEventTypeCode());
        po.setExecutionMode(m.getExecutionMode() == null ? null : m.getExecutionMode().name());
        po.setSubject(m.getSubject() == null ? null : m.getSubject().name());
        po.setGradingMode(m.getGradingMode() == null ? null : m.getGradingMode().name());
        po.setStatus(m.getStatus());
        po.setVersion(m.getVersion());
        return po;
    }

    private RatingModel toDomain(RatingModelPO po) {
        List<RatingModel.GradeBand> bands = gradeBandMapper.selectList(
                        new LambdaQueryWrapper<RatingGradeBandPO>()
                                .eq(RatingGradeBandPO::getRatingModelId, po.getId())
                                .orderByAsc(RatingGradeBandPO::getOrderNo))
                .stream()
                .map(b -> new RatingModel.GradeBand(b.getMinScore(), b.getMaxScore(), b.getGrade(),
                        b.getOrderNo() == null ? 0 : b.getOrderNo()))
                .toList();
        List<RatingModel.RatingItem> items = itemMapper.selectList(
                        new LambdaQueryWrapper<RatingItemPO>()
                                .eq(RatingItemPO::getRatingModelId, po.getId())
                                .orderByAsc(RatingItemPO::getId))
                .stream()
                .map(i -> new RatingModel.RatingItem(i.getCategory(), i.getSubItem(),
                        i.getConditionExpr(), i.getScore(), i.getSubItemCap(),
                        i.getImportance(), i.getGrade()))
                .toList();
        return RatingModel.rehydrate(po.getId(), po.getName(), po.getEventTypeCode(),
                parseExecutionMode(po.getExecutionMode()), parseSubject(po.getSubject()),
                parseGradingMode(po.getGradingMode()), bands, items,
                po.getStatus(), po.getVersion() == null ? 1 : po.getVersion());
    }

    private RatingModel.ExecutionMode parseExecutionMode(String raw) {
        return raw == null ? null : RatingModel.ExecutionMode.valueOf(raw);
    }

    private RatingModel.Subject parseSubject(String raw) {
        return raw == null ? null : RatingModel.Subject.valueOf(raw);
    }

    private RatingModel.GradingMode parseGradingMode(String raw) {
        return raw == null ? null : RatingModel.GradingMode.valueOf(raw);
    }
}
