package com.riskplatform.ruleconfig.domain.audit;

/**
 * 审计日志仓储端口（R17.3）。
 *
 * <p>领域层只依赖该抽象，基础设施层提供 MyBatis-Plus 实现。
 */
public interface AuditLogRepository {

    /** 持久化一条审计日志。 */
    AuditLog save(AuditLog auditLog);
}
