package com.riskplatform.ruleconfig.domain.reference;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.error.RuleConfigErrorCode;

/**
 * 跨模块引用校验领域服务（risk-console-redesign R14.1/R14.2，任务 2.3）。
 *
 * <p>可复用于规则、决策流、评级模型：当其引用某事件或某事件字段时，先经本服务校验被引用
 * 对象是否在参数管理中真实存在；不存在则拒绝并返回 {@code REF.NOT_FOUND}（Property 38）。
 *
 * <p>为各子域（任务 8/11/14）提供干净的接入点：在解析到事件/事件字段引用处调用
 * {@link #requireEvent} / {@link #requireEventField} 即可，无需各自重复实现存在性查询。
 *
 * <p>本类为纯领域对象（不依赖框架），存在性查询委托给 {@link ReferenceResolver}，便于以
 * 内存假体进行单元/属性测试。
 */
public class ReferenceValidator {

    private final ReferenceResolver resolver;

    public ReferenceValidator(ReferenceResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * 校验事件引用存在；不存在抛 {@link BizException}（{@code REF.NOT_FOUND}，R14.2）。
     *
     * @param eventCode 被引用的事件 code
     */
    public void requireEvent(String eventCode) {
        if (eventCode == null || eventCode.isBlank() || !resolver.eventExists(eventCode)) {
            throw new BizException(RuleConfigErrorCode.REF_NOT_FOUND,
                    "引用的事件不存在: " + eventCode);
        }
    }

    /**
     * 校验事件字段引用存在；不存在抛 {@link BizException}（{@code REF.NOT_FOUND}，R14.2）。
     *
     * <p>先要求事件本身存在，再要求该事件下存在对应字段关联。
     *
     * @param eventCode 事件 code
     * @param fieldCode 事件字段 code
     */
    public void requireEventField(String eventCode, String fieldCode) {
        if (eventCode == null || eventCode.isBlank() || !resolver.eventExists(eventCode)) {
            throw new BizException(RuleConfigErrorCode.REF_NOT_FOUND,
                    "引用的事件不存在: " + eventCode);
        }
        if (fieldCode == null || fieldCode.isBlank()
                || !resolver.eventFieldExists(eventCode, fieldCode)) {
            throw new BizException(RuleConfigErrorCode.REF_NOT_FOUND,
                    "引用的事件字段不存在: " + eventCode + "." + fieldCode);
        }
    }

    /**
     * 非异常风格的事件引用判定，便于批量场景预检。
     *
     * @return 存在返回 {@code true}
     */
    public boolean eventExists(String eventCode) {
        return eventCode != null && !eventCode.isBlank() && resolver.eventExists(eventCode);
    }

    /**
     * 非异常风格的事件字段引用判定，便于批量场景预检。
     *
     * @return 存在返回 {@code true}
     */
    public boolean eventFieldExists(String eventCode, String fieldCode) {
        return eventExists(eventCode)
                && fieldCode != null && !fieldCode.isBlank()
                && resolver.eventFieldExists(eventCode, fieldCode);
    }
}
