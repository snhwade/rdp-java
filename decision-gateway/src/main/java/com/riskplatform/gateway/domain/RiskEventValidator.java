package com.riskplatform.gateway.domain;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.common.error.ValidationException;

import java.util.Map;

/**
 * 风控事件受理校验（R2.2–R2.5）。
 *
 * <p>校验：必填字段（eventTypeCode/context）、code 长度 ≤64、上下文序列化大小 ≤64KB。
 * 事件类型存在且启用由 {@link EventTypeStatusChecker} 判定。
 */
public class RiskEventValidator {

    public static final int CODE_MAX = 64;
    public static final int CONTEXT_MAX_BYTES = 64 * 1024;

    private final EventTypeStatusChecker statusChecker;

    public RiskEventValidator(EventTypeStatusChecker statusChecker) {
        this.statusChecker = statusChecker;
    }

    /**
     * 事件类型状态检查端口：返回 存在且启用/存在但禁用/不存在。
     */
    public interface EventTypeStatusChecker {
        Status check(String eventTypeCode);

        enum Status { ENABLED, DISABLED, NOT_FOUND }
    }

    /**
     * 校验事件请求。校验失败抛出异常（不生成事件标识、不触发匹配）。
     *
     * @param eventTypeCode   事件类型 code
     * @param context         事件上下文
     * @param contextSizeBytes 上下文序列化后字节数
     */
    public void validate(String eventTypeCode, Map<String, Object> context, int contextSizeBytes) {
        // R2.2 必填字段
        ValidationException.Builder errors = ValidationException.builder();
        if (eventTypeCode == null || eventTypeCode.isEmpty()) {
            errors.field("eventTypeCode", "必填");
        }
        if (context == null) {
            errors.field("context", "必填");
        }
        errors.throwIfAny();

        // R2.5 大小限制
        if (eventTypeCode.length() > CODE_MAX || contextSizeBytes > CONTEXT_MAX_BYTES) {
            throw new BizException(CommonErrorCode.REQUEST_TOO_LARGE,
                    "请求超出大小限制：code≤" + CODE_MAX + "，context≤" + CONTEXT_MAX_BYTES + " 字节");
        }

        // R2.3/R2.4 事件类型存在且启用
        EventTypeStatusChecker.Status status = statusChecker.check(eventTypeCode);
        if (status == EventTypeStatusChecker.Status.NOT_FOUND) {
            throw BizException.notFound("事件类型不存在: " + eventTypeCode);
        }
        if (status == EventTypeStatusChecker.Status.DISABLED) {
            throw BizException.invalidState("事件类型已禁用: " + eventTypeCode);
        }
    }
}
