package com.riskplatform.ruleconfig.infrastructure.field;

import com.riskplatform.ruleconfig.domain.field.CompositeFieldReferenceChecker;
import com.riskplatform.ruleconfig.domain.field.FieldReferenceChecker;
import com.riskplatform.ruleconfig.domain.field.FieldReferenceSource;
import com.riskplatform.ruleconfig.infrastructure.reference.CrossDomainReferenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;

import java.util.List;

/**
 * 全局字段血缘检查装配（参数管理 Q1-B）。
 */
@Configuration
public class FieldReferenceCheckerConfig {

    private static final Logger log = LoggerFactory.getLogger(FieldReferenceCheckerConfig.class);

    @Bean
    public FieldReferenceSource eventFieldLibraryReferenceSource(CrossDomainReferenceMapper mapper) {
        return new MapperFieldIdReferenceSource(mapper, "事件字段");
    }

    @Bean
    public FieldReferenceSource rulePackageFieldReferenceSource(CrossDomainReferenceMapper mapper) {
        return new MapperFieldCodeReferenceSource(mapper, "规则包", MapperFieldCodeReferenceSource.Target.RULE);
    }

    @Bean
    public FieldReferenceSource decisionFlowFieldReferenceSource(CrossDomainReferenceMapper mapper) {
        return new MapperFieldCodeReferenceSource(mapper, "决策流", MapperFieldCodeReferenceSource.Target.FLOW);
    }

    @Bean
    public FieldReferenceSource indicatorFieldReferenceSource(CrossDomainReferenceMapper mapper) {
        return new MapperFieldCodeReferenceSource(mapper, "指标", MapperFieldCodeReferenceSource.Target.INDICATOR);
    }

    @Bean
    @Primary
    public FieldReferenceChecker compositeFieldReferenceChecker(List<FieldReferenceSource> sources) {
        return new CompositeFieldReferenceChecker(sources);
    }

    static final class MapperFieldIdReferenceSource implements FieldReferenceSource {
        private final CrossDomainReferenceMapper mapper;
        private final String referenceType;

        MapperFieldIdReferenceSource(CrossDomainReferenceMapper mapper, String referenceType) {
            this.mapper = mapper;
            this.referenceType = referenceType;
        }

        @Override
        public String referenceType() {
            return referenceType;
        }

        @Override
        public boolean isReferenced(Long fieldId, String fieldCode) {
            if (fieldId == null) {
                return false;
            }
            try {
                Integer count = mapper.countEventFieldByFieldId(fieldId);
                return count != null && count > 0;
            } catch (DataAccessException ex) {
                log.debug("字段引用来源[{}]查询失败，按无引用处理: {}", referenceType, ex.getMessage());
                return false;
            }
        }
    }

    static final class MapperFieldCodeReferenceSource implements FieldReferenceSource {
        enum Target { RULE, FLOW, INDICATOR }

        private final CrossDomainReferenceMapper mapper;
        private final String referenceType;
        private final Target target;

        MapperFieldCodeReferenceSource(CrossDomainReferenceMapper mapper, String referenceType, Target target) {
            this.mapper = mapper;
            this.referenceType = referenceType;
            this.target = target;
        }

        @Override
        public String referenceType() {
            return referenceType;
        }

        @Override
        public boolean isReferenced(Long fieldId, String fieldCode) {
            if (fieldCode == null || fieldCode.isBlank()) {
                return false;
            }
            String pattern = "%" + fieldCode + "%";
            try {
                Integer count = switch (target) {
                    case RULE -> mapper.countRuleByFieldPattern(pattern);
                    case FLOW -> mapper.countDecisionFlowByFieldPattern(pattern);
                    case INDICATOR -> mapper.countIndicatorByFieldPattern(pattern);
                };
                return count != null && count > 0;
            } catch (DataAccessException ex) {
                log.debug("字段引用来源[{}]查询失败，按无引用处理: {}", referenceType, ex.getMessage());
                return false;
            }
        }
    }
}
