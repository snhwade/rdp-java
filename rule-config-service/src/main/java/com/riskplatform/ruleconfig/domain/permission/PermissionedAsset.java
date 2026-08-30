package com.riskplatform.ruleconfig.domain.permission;

import java.util.List;

/**
 * 受数据权限管控的资产视图（R11.3）。
 *
 * <p>后续阶段的规则包、决策流等资产实现本接口（或由调用方用
 * {@code DataPermissionFilter.filter(list, ownerOrgFn, scenariosFn)} 适配），
 * 即可被 {@code DataPermissionFilter} 统一过滤，无需在过滤器中耦合具体资产类型。
 */
public interface PermissionedAsset {

    /** 资产所属机构 id；返回 null 表示未设置（按「机构字段必填」开关处理，R11.5）。 */
    Long ownerOrgId();

    /** 资产所属场景编码集合；无所属场景返回空列表。 */
    List<String> scenarioCodes();
}
