package com.riskplatform.ruleconfig.application.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.domain.audit.AuditLog;
import com.riskplatform.ruleconfig.domain.audit.AuditLogRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计日志切面（R17.3，任务 19.2）。
 *
 * <p>拦截标注 {@link Audited} 的应用服务写方法（事件类型/规则/规则组/指标定义的
 * 创建、更新、删除），在方法成功返回后写入 {@code audit_log}，记录：
 * <ul>
 *   <li>操作人 operator：取自 Spring Security 的 {@code SecurityContext}（19.1 注入的 JWT 主体）；</li>
 *   <li>操作时间 opTime：方法返回时刻；</li>
 *   <li>操作内容 opContent：对象类型、操作类型、方法名与入参的 JSON 串。</li>
 * </ul>
 *
 * <p>设计取舍：仅在业务方法成功（未抛异常）后记录，避免把失败的非法操作记成生效操作；
 * 审计写入失败不影响主业务返回（吞掉异常并告警），保证审计为旁路增强而非阻塞链路。
 */
@Aspect
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);
    private static final String ANONYMOUS = "anonymous";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogAspect(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 环绕通知：先执行业务方法，成功后记录审计日志。
     *
     * <p>使用 {@code @annotation(audited)} 直接绑定方法上的注解元数据，
     * 仅匹配带 {@link Audited} 的方法。
     */
    @Around("@annotation(audited)")
    public Object recordAudit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Object result = pjp.proceed();
        try {
            AuditLog auditLog = new AuditLog(
                    currentOperator(),
                    LocalDateTime.now(),
                    audited.op(),
                    audited.target(),
                    buildContent(pjp, audited));
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            // 审计为旁路增强：写入失败仅告警，不影响已成功的业务操作
            log.warn("写入审计日志失败 target={} op={} : {}",
                    audited.target(), audited.op(), ex.getMessage(), ex);
        }
        return result;
    }

    /** 取当前操作人；未认证时记为 anonymous。 */
    private String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return ANONYMOUS;
        }
        return auth.getName();
    }

    /** 构造操作内容 JSON：对象类型、操作类型、方法名与入参。 */
    private String buildContent(ProceedingJoinPoint pjp, Audited audited) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("targetType", audited.target().code());
        content.put("opType", audited.op().name());
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        content.put("method", signature.getName());
        content.put("args", buildArgs(signature.getParameterNames(), pjp.getArgs()));
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            // 个别入参无法序列化时降级为方法名+操作类型，保证审计仍可落库
            return "{\"targetType\":\"" + audited.target().code()
                    + "\",\"opType\":\"" + audited.op().name()
                    + "\",\"method\":\"" + signature.getName() + "\"}";
        }
    }

    /** 将形参名与实参值组装为可序列化映射；无形参名时回退为 argN。 */
    private Map<String, Object> buildArgs(String[] names, Object[] args) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (args == null) {
            return map;
        }
        for (int i = 0; i < args.length; i++) {
            String key = (names != null && i < names.length && names[i] != null) ? names[i] : ("arg" + i);
            map.put(key, args[i]);
        }
        return map;
    }
}
