package com.riskplatform.ruleconfig.application.permission;

import java.util.List;
import java.util.Set;

/**
 * 当前登录用户的数据权限上下文（R11.3）。
 *
 * @param username        用户名（来自 JWT subject）
 * @param orgPath         用户所在机构的物化路径（如 {@code /1/4/9/}）；超级管理员可为 null
 * @param scenarioCodes   用户拥有的场景编码集合
 * @param superAdmin      是否超级管理员（全可见，绕过数据权限过滤）
 */
public record UserDataContext(String username, String orgPath, Set<String> scenarioCodes, boolean superAdmin) {

    /** 超级管理员上下文：全部资产可见，不按机构/场景过滤。 */
    public static UserDataContext superAdmin(String username) {
        return new UserDataContext(username, null, Set.of(), true);
    }

    /** 普通用户上下文。 */
    public static UserDataContext of(String username, String orgPath, List<String> scenarioCodes) {
        return new UserDataContext(
                username,
                orgPath,
                scenarioCodes == null ? Set.of() : Set.copyOf(scenarioCodes),
                false);
    }
}
