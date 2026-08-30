package com.riskplatform.ruleconfig.domain.error;

import com.riskplatform.common.error.ErrorCategory;
import com.riskplatform.common.error.ErrorCode;

/**
 * rule-config-service 专有业务错误码（risk-console-redesign）。
 *
 * <p>补充 {@code commons-core} 的 {@code CommonErrorCode} 之外、本服务特有的业务错误码：
 * <ul>
 *   <li>{@link #EVENT_HAS_DEPENDENCY}（{@code EVENT.HAS_DEPENDENCY}）：删除事件时仍存在
 *       关联的事件字段/规则包/决策流/评级模型（R2.9，Property 6）。</li>
 *   <li>{@link #EVENT_FIELD_IN_USE}（{@code EVENT_FIELD.IN_USE}）：移除事件字段时仍被
 *       规则或评级模型引用（R4.7，预留给任务 4.2）。</li>
 *   <li>{@link #FIELD_IN_USE}（{@code FIELD.IN_USE}）：删除或改字段 code 时仍被
 *       事件字段/规则包/决策流/指标引用（参数管理 Q1-B）。</li>
 *   <li>{@link #REF_NOT_FOUND}（{@code REF.NOT_FOUND}）：规则/决策流/评级模型引用了参数
 *       管理中不存在的事件或事件字段（R14.2，Property 38）。</li>
 * </ul>
 */
public enum RuleConfigErrorCode implements ErrorCode {

    EVENT_HAS_DEPENDENCY("EVENT.HAS_DEPENDENCY", "事件存在关联依赖，无法删除", ErrorCategory.BUSINESS),
    EVENT_FIELD_IN_USE("EVENT_FIELD.IN_USE", "事件字段仍被引用，无法移除", ErrorCategory.BUSINESS),
    FIELD_IN_USE("FIELD.IN_USE", "字段仍被引用，无法删除或修改编码", ErrorCategory.BUSINESS),
    REF_NOT_FOUND("REF.NOT_FOUND", "被引用的对象不存在", ErrorCategory.BUSINESS);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    RuleConfigErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
