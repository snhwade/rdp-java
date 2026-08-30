package com.riskplatform.ruleconfig.domain.audit;

/**
 * 审计操作类型（R17.3）。
 *
 * <p>对应 audit_log.op_type：创建/更新/删除。
 */
public enum AuditOpType {
    /** 创建。 */
    CREATE,
    /** 更新（含启用/禁用、关联规则、选择器配置等状态变更）。 */
    UPDATE,
    /** 删除。 */
    DELETE
}
