package com.riskplatform.ruleconfig.application.ratingmodel;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModel;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModelRepository;
import com.riskplatform.ruleconfig.domain.reference.ReferenceValidator;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评级模型应用服务（risk-console-redesign，R10）。
 *
 * <p>编排评级模型的卡片墙列表、创建、详情（三页签：评级模型/源码/版本历史）、保存→新建版本、上下线。
 *
 * <p>校验责任划分：
 * <ul>
 *   <li>执行方式 ∈ {REALTIME,SCHEDULED}、评级主体 ∈ {MERCHANT,INDIVIDUAL}、定级方式枚举合法性
 *       → 由适配器层解析入参为 Java 枚举时天然保证（非法字符串无法构造枚举），
 *       并由 {@link RatingModel#validate()} 断言三枚举非空（R10.3）。</li>
 *   <li>所属事件须真实存在 → 经 {@link ReferenceValidator#requireEvent} 校验（R14.2，引用不存在即拒绝）。</li>
 *   <li>等级区间结构与重叠/覆盖校验 → {@link RatingModel#validateGradeBands()}（基础结构校验，
 *       重叠/覆盖缺口判定由任务 14.3 在该方法内补全）。</li>
 * </ul>
 *
 * <p>每次创建/保存成功后调用 {@link RatingModelVersionAppService#snapshot} 写入版本快照
 * （版本号递增），供「源码」与「版本历史」页签消费（R10.5/R10.6）。
 */
public class RatingModelAppService {

    private final RatingModelRepository repository;
    private final RatingModelVersionAppService versionAppService;
    private final ReferenceValidator referenceValidator;
    private final ConfigChangePublisher configChangePublisher;

    public RatingModelAppService(RatingModelRepository repository,
                                 RatingModelVersionAppService versionAppService,
                                 ReferenceValidator referenceValidator,
                                 ConfigChangePublisher configChangePublisher) {
        this.repository = repository;
        this.versionAppService = versionAppService;
        this.referenceValidator = referenceValidator;
        this.configChangePublisher = configChangePublisher;
    }

    /**
     * 创建评级模型（R10.2/R10.3）。仅以名称 + 所属事件 + 执行方式 + 评级主体 + 定级方式创建，
     * 等级区间与子项初始为空，由后续保存补全。所属事件须存在（R14.2）。
     */
    @Transactional
    public RatingModel create(String name, String eventTypeCode, RatingModel.ExecutionMode executionMode,
                              RatingModel.Subject subject, RatingModel.GradingMode gradingMode) {
        referenceValidator.requireEvent(eventTypeCode);
        RatingModel model = RatingModel.create(name, eventTypeCode, executionMode, subject, gradingMode);
        RatingModel saved = repository.save(model);
        versionAppService.snapshot(saved);
        configChangePublisher.publishChange("RATING_MODEL", String.valueOf(saved.getId()));
        return saved;
    }

    /**
     * 保存评级模型配置→新建版本（R10.6）：更新名称/定级方式/等级区间/子项，版本号递增并写入新快照。
     *
     * <p>保存前执行 {@link RatingModel#validateGradeBands()} 等级区间校验。
     */
    @Transactional
    public RatingModel save(Long id, String name, RatingModel.GradingMode gradingMode,
                            List<RatingModel.GradeBand> gradeBands, List<RatingModel.RatingItem> items) {
        RatingModel model = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("评级模型不存在: id=" + id));
        model.update(name, gradingMode, gradeBands, items);
        model.validateGradeBands();
        model.bumpVersion();
        RatingModel saved = repository.update(model);
        versionAppService.snapshot(saved);
        configChangePublisher.publishChange("RATING_MODEL", String.valueOf(id));
        return saved;
    }

    /** 评级模型卡片墙列表（R10.1），可按所属事件筛选；未提供事件时返回全部。 */
    public List<RatingModel> list(String eventTypeCode) {
        return (eventTypeCode == null || eventTypeCode.isBlank())
                ? repository.findAll() : repository.findByEventTypeCode(eventTypeCode);
    }

    /** 评级模型详情（R10.4）：聚合根基础属性 + 等级区间 + 子项。 */
    public RatingModel get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("评级模型不存在: id=" + id));
    }

    /** 上线评级模型（R10.7）。 */
    @Transactional
    public RatingModel online(Long id) {
        RatingModel model = get(id);
        model.online();
        RatingModel saved = repository.update(model);
        configChangePublisher.publishChange("RATING_MODEL", String.valueOf(id));
        return saved;
    }

    /** 下线评级模型（R10.7）。 */
    @Transactional
    public RatingModel offline(Long id) {
        RatingModel model = get(id);
        model.offline();
        RatingModel saved = repository.update(model);
        configChangePublisher.publishChange("RATING_MODEL", String.valueOf(id));
        return saved;
    }
}
