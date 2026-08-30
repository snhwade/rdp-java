package com.riskplatform.screening.domain.list;

import com.riskplatform.common.error.ValidationException;

import java.time.LocalDateTime;

/**
 * 名单记录聚合根（S1 名单管理增强）。
 *
 * <p>一条名单记录：类型（黑/白/关注）+ 维度（如 merchantId/idNo/name）+ 维度值 + 可选有效期。
 * 黑名单命中→拦截；白名单命中→对 immuneRuleId 指定规则免疫；关注名单命中→标记。
 *
 * <p>不变式：listType/dimension/dimensionValue 必填；dimensionValue 长度 1..512。
 *
 * @param id            记录 id
 * @param listType      名单类型
 * @param dimension     维度字段名（如 merchantId、idNo、subjectName）
 * @param dimensionValue 维度值
 * @param reason        加入名单原因（可空）
 * @param immuneRuleId  白名单免疫的规则 id（仅 WHITE 有意义，可空表示对所有规则免疫）
 * @param expireAt      到期时间（可空表示长期有效）
 * @param enabled       是否启用
 */
public record ListRecord(
        Long id,
        ListType listType,
        String dimension,
        String dimensionValue,
        String reason,
        Long immuneRuleId,
        LocalDateTime expireAt,
        boolean enabled) {

    public static final int VALUE_MAX = 512;

    /** 创建并校验不变式。 */
    public static ListRecord create(ListType listType, String dimension, String dimensionValue,
                                     String reason, Long immuneRuleId, LocalDateTime expireAt) {
        ValidationException.Builder errors = ValidationException.builder();
        if (listType == null) {
            errors.field("listType", "必填");
        }
        if (dimension == null || dimension.isBlank()) {
            errors.field("dimension", "必填");
        }
        if (dimensionValue == null || dimensionValue.isBlank()) {
            errors.field("dimensionValue", "必填");
        } else if (dimensionValue.length() > VALUE_MAX) {
            errors.field("dimensionValue", "长度不能超过 " + VALUE_MAX);
        }
        errors.throwIfAny();
        return new ListRecord(null, listType, dimension, dimensionValue, reason, immuneRuleId, expireAt, true);
    }

    /** 是否在指定时刻有效（启用且未过期）。 */
    public boolean isActiveAt(LocalDateTime now) {
        if (!enabled) {
            return false;
        }
        return expireAt == null || expireAt.isAfter(now);
    }
}
