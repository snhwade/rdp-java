package com.riskplatform.screening.domain.list;

/**
 * 名单类型（S1 名单管理增强）。
 *
 * <ul>
 *   <li>BLACK：黑名单，命中后产出拦截信号（REJECT）；</li>
 *   <li>WHITE：白名单，命中后对指定规则免疫（跳过）；</li>
 *   <li>WATCH：关注名单，命中仅标记/复核，不直接拦截。</li>
 * </ul>
 */
public enum ListType {
    BLACK,
    WHITE,
    WATCH
}
