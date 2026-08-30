package com.riskplatform.ruleconfig.infrastructure.eventtype;

import com.riskplatform.ruleconfig.domain.eventtype.CompositeEventReferenceChecker;
import com.riskplatform.ruleconfig.domain.eventtype.EventDependencySource;
import com.riskplatform.ruleconfig.domain.eventtype.EventReferenceChecker;
import com.riskplatform.ruleconfig.infrastructure.reference.CrossDomainReferenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;

import java.util.List;

/**
 * 跨子域事件依赖检查装配（risk-console-redesign R2.9，任务 2.3）。
 */
@Configuration
public class EventReferenceCheckerConfig {

    private static final Logger log = LoggerFactory.getLogger(EventReferenceCheckerConfig.class);

    @Bean
    public EventDependencySource eventFieldDependencySource(CrossDomainReferenceMapper mapper) {
        return new MapperEventDependencySource(mapper, "事件字段", MapperEventDependencySource.Target.EVENT_FIELD);
    }

    @Bean
    public EventDependencySource rulePackageDependencySource(CrossDomainReferenceMapper mapper) {
        return new MapperEventDependencySource(mapper, "规则包", MapperEventDependencySource.Target.RULE_PACKAGE);
    }

    @Bean
    public EventDependencySource decisionFlowDependencySource(CrossDomainReferenceMapper mapper) {
        return new MapperEventDependencySource(mapper, "决策流", MapperEventDependencySource.Target.DECISION_FLOW);
    }

    @Bean
    public EventDependencySource ratingModelDependencySource(CrossDomainReferenceMapper mapper) {
        return new MapperEventDependencySource(mapper, "评级模型", MapperEventDependencySource.Target.RATING_MODEL);
    }

    @Bean
    @Primary
    public EventReferenceChecker compositeEventReferenceChecker(List<EventDependencySource> sources) {
        return new CompositeEventReferenceChecker(sources);
    }

    static final class MapperEventDependencySource implements EventDependencySource {

        enum Target { EVENT_FIELD, RULE_PACKAGE, DECISION_FLOW, RATING_MODEL }

        private final CrossDomainReferenceMapper mapper;
        private final String dependencyType;
        private final Target target;

        MapperEventDependencySource(CrossDomainReferenceMapper mapper,
                                    String dependencyType,
                                    Target target) {
            this.mapper = mapper;
            this.dependencyType = dependencyType;
            this.target = target;
        }

        @Override
        public String dependencyType() {
            return dependencyType;
        }

        @Override
        public boolean hasDependency(String eventCode) {
            try {
                Integer count = switch (target) {
                    case EVENT_FIELD -> mapper.countEventFieldByEventCode(eventCode);
                    case RULE_PACKAGE -> mapper.countRulePackageEventByEventCode(eventCode);
                    case DECISION_FLOW -> mapper.countDecisionFlowByEventCode(eventCode);
                    case RATING_MODEL -> mapper.countRatingModelByEventCode(eventCode);
                };
                return count != null && count > 0;
            } catch (DataAccessException ex) {
                log.debug("依赖来源[{}]查询失败，按无依赖处理: {}", dependencyType, ex.getMessage());
                return false;
            }
        }
    }
}
