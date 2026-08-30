package com.riskplatform.ruleconfig.infrastructure.eventfield;

import com.riskplatform.ruleconfig.domain.eventfield.CompositeEventFieldReferenceChecker;
import com.riskplatform.ruleconfig.domain.eventfield.EventField;
import com.riskplatform.ruleconfig.domain.eventfield.EventFieldReferenceChecker;
import com.riskplatform.ruleconfig.domain.eventfield.EventFieldReferenceSource;
import com.riskplatform.ruleconfig.infrastructure.reference.CrossDomainReferenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;

import java.util.List;

/**
 * 事件字段引用检查装配（risk-console-redesign R4.7，任务 4.2）。
 */
@Configuration
public class EventFieldReferenceCheckerConfig {

    private static final Logger log = LoggerFactory.getLogger(EventFieldReferenceCheckerConfig.class);

    @Bean
    public EventFieldReferenceSource ruleEventFieldReferenceSource(CrossDomainReferenceMapper mapper) {
        return new MapperEventFieldReferenceSource(mapper, "规则", MapperEventFieldReferenceSource.Target.RULE);
    }

    @Bean
    public EventFieldReferenceSource ratingModelEventFieldReferenceSource(CrossDomainReferenceMapper mapper) {
        return new MapperEventFieldReferenceSource(mapper, "评级模型", MapperEventFieldReferenceSource.Target.RATING);
    }

    @Bean
    @Primary
    public EventFieldReferenceChecker compositeEventFieldReferenceChecker(
            List<EventFieldReferenceSource> sources) {
        return new CompositeEventFieldReferenceChecker(sources);
    }

    static final class MapperEventFieldReferenceSource implements EventFieldReferenceSource {

        enum Target { RULE, RATING }

        private final CrossDomainReferenceMapper mapper;
        private final String referenceType;
        private final Target target;

        MapperEventFieldReferenceSource(CrossDomainReferenceMapper mapper,
                                        String referenceType,
                                        Target target) {
            this.mapper = mapper;
            this.referenceType = referenceType;
            this.target = target;
        }

        @Override
        public String referenceType() {
            return referenceType;
        }

        @Override
        public boolean isReferenced(EventField eventField) {
            if (eventField == null || eventField.getEventTypeCode() == null
                    || eventField.getFieldId() == null) {
                return false;
            }
            String fieldCode = resolveFieldCode(eventField.getFieldId());
            if (fieldCode == null || fieldCode.isBlank()) {
                return false;
            }
            try {
                String pattern = "%" + fieldCode + "%";
                Integer count = switch (target) {
                    case RULE -> mapper.countRuleByEventAndFieldPattern(eventField.getEventTypeCode(), pattern);
                    case RATING -> mapper.countRatingByEventAndFieldPattern(eventField.getEventTypeCode(), pattern);
                };
                return count != null && count > 0;
            } catch (DataAccessException ex) {
                log.debug("引用来源[{}]查询失败，按无引用处理: {}", referenceType, ex.getMessage());
                return false;
            }
        }

        private String resolveFieldCode(Long fieldId) {
            try {
                return mapper.findFieldCodeById(fieldId);
            } catch (DataAccessException ex) {
                log.debug("字段 code 解析失败，按无引用处理: fieldId={}, {}", fieldId, ex.getMessage());
                return null;
            }
        }
    }
}
