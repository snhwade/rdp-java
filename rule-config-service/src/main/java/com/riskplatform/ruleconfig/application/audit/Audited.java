package com.riskplatform.ruleconfig.application.audit;

import com.riskplatform.ruleconfig.domain.audit.AuditOpType;
import com.riskplatform.ruleconfig.domain.audit.AuditTargetType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计标注（R17.3）。
 *
 * <p>标注在应用服务的写操作方法（创建/更新/删除）上，由
 * {@link AuditLogAspect} 拦截，在方法成功返回后写入 {@code audit_log}
 * （操作人/操作时间/操作内容）。
 *
 * <p>采用方法级注解而非按 URL 拦截，是因为四类受审计对象中规则组目前仅有应用服务、
 * 尚无独立 REST 控制器，注解可统一覆盖且与传输层解耦。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /** 受审计的对象类型。 */
    AuditTargetType target();

    /** 操作类型：创建/更新/删除。 */
    AuditOpType op();
}
