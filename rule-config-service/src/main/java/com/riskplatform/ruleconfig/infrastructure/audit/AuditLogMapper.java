package com.riskplatform.ruleconfig.infrastructure.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * audit_log 表 MyBatis-Plus Mapper（R17.3）。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogPO> {
}
