package com.riskplatform.ruleconfig.infrastructure.reference;

import com.riskplatform.ruleconfig.domain.eventtype.EventTypeRepository;
import com.riskplatform.ruleconfig.domain.reference.ReferenceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 参数管理对象存在性解析实现（risk-console-redesign R14.1/R14.2，任务 2.3）。
 */
@Component
public class RepositoryReferenceResolver implements ReferenceResolver {

    private static final Logger log = LoggerFactory.getLogger(RepositoryReferenceResolver.class);

    private final EventTypeRepository eventTypeRepository;
    private final CrossDomainReferenceMapper referenceMapper;

    public RepositoryReferenceResolver(EventTypeRepository eventTypeRepository,
                                       CrossDomainReferenceMapper referenceMapper) {
        this.eventTypeRepository = eventTypeRepository;
        this.referenceMapper = referenceMapper;
    }

    @Override
    public boolean eventExists(String eventCode) {
        if (eventCode == null || eventCode.isBlank()) {
            return false;
        }
        return eventTypeRepository.findByCode(eventCode).isPresent();
    }

    @Override
    public boolean eventFieldExists(String eventCode, String fieldCode) {
        if (eventCode == null || eventCode.isBlank() || fieldCode == null || fieldCode.isBlank()) {
            return false;
        }
        try {
            Integer count = referenceMapper.countEventFieldByEventAndFieldCode(eventCode, fieldCode);
            return count != null && count > 0;
        } catch (DataAccessException ex) {
            log.debug("事件字段存在性查询失败，按不存在处理: {}", ex.getMessage());
            return false;
        }
    }
}
