package com.riskplatform.ruleconfig.infrastructure.audit;

import com.riskplatform.ruleconfig.domain.audit.AuditLog;
import com.riskplatform.ruleconfig.domain.audit.AuditLogRepository;
import org.springframework.stereotype.Repository;

/**
 * 审计日志仓储 MyBatis-Plus 实现（R17.3）。
 */
@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogMapper mapper;

    public AuditLogRepositoryImpl(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AuditLog save(AuditLog auditLog) {
        AuditLogPO po = new AuditLogPO();
        po.setOperator(auditLog.getOperator());
        po.setOpTime(auditLog.getOpTime());
        po.setOpType(auditLog.getOpType().name());
        po.setTargetType(auditLog.getTargetType().code());
        po.setOpContent(auditLog.getOpContent());
        mapper.insert(po);
        auditLog.setId(po.getId());
        return auditLog;
    }
}
