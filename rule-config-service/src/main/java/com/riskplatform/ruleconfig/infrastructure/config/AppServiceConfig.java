package com.riskplatform.ruleconfig.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.web.GlobalExceptionHandler;
import com.riskplatform.ruleconfig.application.approval.ApprovalService;
import com.riskplatform.ruleconfig.application.audit.AuditLogAspect;
import com.riskplatform.ruleconfig.domain.audit.AuditLogRepository;
import com.riskplatform.ruleconfig.application.assetversion.AssetVersionService;
import com.riskplatform.ruleconfig.application.decisionflow.DecisionFlowAppService;
import com.riskplatform.ruleconfig.application.decisionflow.DecisionFlowVersionAppService;
import com.riskplatform.ruleconfig.application.field.FieldService;
import com.riskplatform.ruleconfig.application.decisionmatrix.DecisionMatrixAppService;
import com.riskplatform.ruleconfig.application.decisiontable.DecisionTableAppService;
import com.riskplatform.ruleconfig.application.decisiontree.DecisionTreeAppService;
import com.riskplatform.ruleconfig.application.eventfield.EventFieldAppService;
import com.riskplatform.ruleconfig.application.eventtype.EventTypeAppService;
import com.riskplatform.ruleconfig.application.indicator.IndicatorDefinitionAppService;
import com.riskplatform.ruleconfig.application.indicator.IndicatorDefinitionSnapshotAppService;
import com.riskplatform.ruleconfig.application.indicator.IndicatorGroupAppService;
import com.riskplatform.ruleconfig.application.indicator.LogicalIndicatorAppService;
import com.riskplatform.ruleconfig.application.permission.UserContextProvider;
import com.riskplatform.ruleconfig.application.ratingmodel.RatingModelAppService;
import com.riskplatform.ruleconfig.application.ratingmodel.RatingModelVersionAppService;
import com.riskplatform.ruleconfig.application.scorecard.ScorecardAppService;
import com.riskplatform.ruleconfig.domain.approval.ApprovalEffectuator;
import com.riskplatform.ruleconfig.domain.approval.ApprovalRequestRepository;
import com.riskplatform.ruleconfig.domain.assetversion.AssetVersionRepository;
import com.riskplatform.ruleconfig.domain.config.ConfigChangePublisher;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowRepository;
import com.riskplatform.ruleconfig.domain.decisionflow.DecisionFlowVersionRepository;
import com.riskplatform.ruleconfig.domain.decisionmatrix.DecisionMatrixRepository;
import com.riskplatform.ruleconfig.domain.decisiontable.DecisionTableRepository;
import com.riskplatform.ruleconfig.domain.decisiontree.DecisionTreeRepository;
import com.riskplatform.ruleconfig.domain.eventfield.EventFieldReferenceChecker;
import com.riskplatform.ruleconfig.domain.eventfield.EventFieldRepository;
import com.riskplatform.ruleconfig.domain.eventtype.EventEngineStatusQuery;
import com.riskplatform.ruleconfig.domain.eventtype.EventReferenceChecker;
import com.riskplatform.ruleconfig.domain.eventtype.EventTypeRepository;
import com.riskplatform.ruleconfig.domain.field.FieldReferenceChecker;
import com.riskplatform.ruleconfig.domain.field.FieldRepository;
import com.riskplatform.ruleconfig.domain.reference.ReferenceResolver;
import com.riskplatform.ruleconfig.domain.reference.ReferenceValidator;
import com.riskplatform.ruleconfig.domain.rulepackage.RulePackageRepository;
import com.riskplatform.ruleconfig.domain.scorecard.ScorecardRepository;
import com.riskplatform.ruleconfig.domain.scorecard.ScorecardRepository;
import com.riskplatform.ruleconfig.infrastructure.approval.LoggingApprovalEffectuator;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorDefinitionRepository;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorGroupRepository;
import com.riskplatform.ruleconfig.domain.indicator.LogicalIndicatorRepository;
import com.riskplatform.ruleconfig.infrastructure.indicator.IndicatorReferenceChecker;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModelRepository;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModelVersionRepository;
import com.riskplatform.ruleconfig.domain.rule.ExpressionValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 应用服务装配与公共组件导入（R1/R3/R5/R14.2）。
 */
@Configuration
@Import(GlobalExceptionHandler.class)
public class AppServiceConfig {

    @Bean
    public EventTypeAppService eventTypeAppService(EventTypeRepository repository,
                                                   EventReferenceChecker referenceChecker,
                                                   EventEngineStatusQuery engineStatusQuery) {
        return new EventTypeAppService(repository, referenceChecker, engineStatusQuery);
    }

    /**
     * 跨模块引用校验领域服务（R14.1/R14.2，任务 2.3）。
     *
     * <p>供规则/决策流/评级模型在解析事件、事件字段引用时复用：引用不存在对象即抛
     * {@code REF.NOT_FOUND}。存在性查询委托 {@link ReferenceResolver}（基础设施层实现）。
     */
    @Bean
    public ReferenceValidator referenceValidator(ReferenceResolver referenceResolver) {
        return new ReferenceValidator(referenceResolver);
    }

    @Bean
    public IndicatorGroupAppService indicatorGroupAppService(
            IndicatorGroupRepository groupRepository,
            IndicatorDefinitionRepository indicatorRepository,
            EventTypeRepository eventTypeRepository) {
        return new IndicatorGroupAppService(groupRepository, indicatorRepository, eventTypeRepository);
    }

    @Bean
    public LogicalIndicatorAppService logicalIndicatorAppService(
            LogicalIndicatorRepository logicalRepository,
            IndicatorDefinitionRepository physicalRepository,
            ExpressionValidator expressionValidator,
            ConfigChangePublisher configChangePublisher,
            IndicatorReferenceChecker referenceChecker) {
        return new LogicalIndicatorAppService(logicalRepository, physicalRepository,
                expressionValidator, configChangePublisher, referenceChecker);
    }

    @Bean
    public IndicatorDefinitionAppService indicatorDefinitionAppService(
            IndicatorDefinitionRepository repository,
            LogicalIndicatorRepository logicalRepository,
            EventTypeRepository eventTypeRepository,
            ExpressionValidator expressionValidator,
            ConfigChangePublisher configChangePublisher,
            IndicatorReferenceChecker referenceChecker,
            IndicatorDefinitionSnapshotAppService snapshotAppService) {
        return new IndicatorDefinitionAppService(repository, logicalRepository, eventTypeRepository,
                expressionValidator, configChangePublisher, referenceChecker, snapshotAppService);
    }

    @Bean
    public DecisionTableAppService decisionTableAppService(
            DecisionTableRepository repository,
            ConfigChangePublisher configChangePublisher) {
        return new DecisionTableAppService(repository, configChangePublisher);
    }

    @Bean
    public ScorecardAppService scorecardAppService(
            ScorecardRepository repository,
            ConfigChangePublisher configChangePublisher) {
        return new ScorecardAppService(repository, configChangePublisher);
    }

    @Bean
    public DecisionFlowVersionAppService decisionFlowVersionAppService(
            DecisionFlowVersionRepository versionRepository,
            DecisionFlowRepository decisionFlowRepository,
            EventTypeRepository eventTypeRepository,
            RulePackageRepository rulePackageRepository,
            DecisionTableRepository decisionTableRepository,
            DecisionTreeRepository decisionTreeRepository,
            DecisionMatrixRepository decisionMatrixRepository,
            ScorecardRepository scorecardRepository,
            ObjectMapper objectMapper,
            UserContextProvider userContextProvider) {
        return new DecisionFlowVersionAppService(
                versionRepository,
                decisionFlowRepository,
                eventTypeRepository,
                rulePackageRepository,
                decisionTableRepository,
                decisionTreeRepository,
                decisionMatrixRepository,
                scorecardRepository,
                objectMapper,
                userContextProvider);
    }

    @Bean
    public DecisionFlowAppService decisionFlowAppService(
            DecisionFlowRepository repository,
            ConfigChangePublisher configChangePublisher,
            DecisionFlowVersionAppService decisionFlowVersionAppService) {
        return new DecisionFlowAppService(repository, configChangePublisher, decisionFlowVersionAppService);
    }

    @Bean
    public ApprovalEffectuator approvalEffectuator(ConfigChangePublisher configChangePublisher) {
        return new LoggingApprovalEffectuator(configChangePublisher);
    }

    @Bean
    public ApprovalService approvalService(ApprovalRequestRepository repository,
                                           ApprovalEffectuator effectuator) {
        return new ApprovalService(repository, effectuator);
    }

    @Bean
    public AssetVersionService assetVersionService(AssetVersionRepository repository) {
        return new AssetVersionService(repository);
    }

    @Bean
    public FieldService fieldService(FieldRepository repository,
                                     FieldReferenceChecker fieldReferenceChecker) {
        return new FieldService(repository, fieldReferenceChecker);
    }

    /**
     * 事件字段应用服务（risk-console-redesign R4，任务 4.2）。
     *
     * <p>编排事件字段的列表/从字段库添加/标记衍生/移除；移除前由
     * {@link EventFieldReferenceChecker} 检查规则/评级模型引用（R4.7）。
     */
    @Bean
    public EventFieldAppService eventFieldAppService(EventFieldRepository repository,
                                                     FieldRepository fieldRepository,
                                                     EventFieldReferenceChecker referenceChecker) {
        return new EventFieldAppService(repository, fieldRepository, referenceChecker);
    }

    @Bean
    public DecisionTreeAppService decisionTreeAppService(
            DecisionTreeRepository repository,
            ConfigChangePublisher configChangePublisher) {
        return new DecisionTreeAppService(repository, configChangePublisher);
    }

    @Bean
    public DecisionMatrixAppService decisionMatrixAppService(
            DecisionMatrixRepository repository,
            ConfigChangePublisher configChangePublisher) {
        return new DecisionMatrixAppService(repository, configChangePublisher);
    }

    /**
     * 评级模型版本应用服务（risk-console-redesign R10.6，任务 14.2）。
     *
     * <p>负责评级模型版本快照写入与版本历史读取，供「源码」「版本历史」页签消费。
     */
    @Bean
    public RatingModelVersionAppService ratingModelVersionAppService(
            RatingModelVersionRepository versionRepository,
            ObjectMapper objectMapper,
            UserContextProvider userContextProvider) {
        return new RatingModelVersionAppService(versionRepository, objectMapper, userContextProvider);
    }

    /**
     * 评级模型应用服务（risk-console-redesign R10，任务 14.2）。
     *
     * <p>编排评级模型卡片墙列表、创建（执行方式/主体枚举校验）、详情三页签、保存→新建版本、上下线；
     * 所属事件存在性经 {@link ReferenceValidator} 校验（R14.2）。
     */
    @Bean
    public RatingModelAppService ratingModelAppService(
            RatingModelRepository repository,
            RatingModelVersionAppService ratingModelVersionAppService,
            ReferenceValidator referenceValidator,
            ConfigChangePublisher configChangePublisher) {
        return new RatingModelAppService(repository, ratingModelVersionAppService,
                referenceValidator, configChangePublisher);
    }

    /**
     * 操作审计切面（R17.3，任务 19.2）。
     *
     * <p>拦截标注 {@code @Audited} 的应用服务写方法，记录操作人/操作时间/操作内容到 audit_log。
     */
    @Bean
    public AuditLogAspect auditLogAspect(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        return new AuditLogAspect(auditLogRepository, objectMapper);
    }
}
