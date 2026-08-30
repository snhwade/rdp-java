package com.riskplatform.ruleconfig.infrastructure.eventtype;

import com.riskplatform.ruleconfig.domain.eventtype.EventEngineStatusQuery;
import com.riskplatform.ruleconfig.infrastructure.reference.CrossDomainReferenceMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 事件引擎可执行状态查询（基于本地配置推断，risk-console-redesign R2.11）。
 */
@Component
public class LocalConfigEventEngineStatusQuery implements EventEngineStatusQuery {

    private final CrossDomainReferenceMapper referenceMapper;

    public LocalConfigEventEngineStatusQuery(CrossDomainReferenceMapper referenceMapper) {
        this.referenceMapper = referenceMapper;
    }

    @Override
    public Status query(String eventCode) {
        if (eventCode == null || eventCode.isBlank()) {
            return Status.UNKNOWN;
        }
        try {
            Integer executable = referenceMapper.isEventExecutable(eventCode);
            return executable != null && executable == 1 ? Status.EXECUTABLE : Status.NOT_EXECUTABLE;
        } catch (DataAccessException ex) {
            return Status.UNKNOWN;
        }
    }
}
