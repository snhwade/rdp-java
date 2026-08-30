package com.riskplatform.gateway.infrastructure.standalone;

import com.riskplatform.gateway.domain.RiskEventValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** standalone：从 MySQL {@code event_type} 校验事件类型状态。 */
@Component
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class DbEventTypeStatusChecker implements RiskEventValidator.EventTypeStatusChecker {

    private final EventTypeReadMapper mapper;

    public DbEventTypeStatusChecker(EventTypeReadMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Status check(String eventTypeCode) {
        Integer status = mapper.selectStatusByCode(eventTypeCode);
        if (status == null) {
            return Status.NOT_FOUND;
        }
        return status == 1 ? Status.ENABLED : Status.DISABLED;
    }
}
