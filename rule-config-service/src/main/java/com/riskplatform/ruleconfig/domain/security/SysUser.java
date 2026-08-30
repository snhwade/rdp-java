package com.riskplatform.ruleconfig.domain.security;

import com.riskplatform.common.error.ValidationException;

import java.util.List;

/**
 * 系统用户（S10）。
 *
 * @param id           主键
 * @param username     用户名（唯一）
 * @param passwordHash BCrypt 密码哈希
 * @param roles        角色列表（ADMIN/OPERATOR/AUDITOR）
 * @param enabled      是否启用
 */
public record SysUser(Long id, String username, String passwordHash, List<String> roles, boolean enabled) {

    public static SysUser create(String username, String passwordHash, List<String> roles) {
        ValidationException.Builder errors = ValidationException.builder();
        if (username == null || username.isBlank()) {
            errors.field("username", "必填");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            errors.field("password", "必填");
        }
        if (roles == null || roles.isEmpty()) {
            errors.field("roles", "至少一个角色");
        }
        errors.throwIfAny();
        return new SysUser(null, username, passwordHash, roles, true);
    }

    public SysUser withEnabled(boolean enabled) {
        return new SysUser(id, username, passwordHash, roles, enabled);
    }

    public SysUser withRoles(List<String> roles) {
        return new SysUser(id, username, passwordHash, roles, enabled);
    }

    public SysUser withPasswordHash(String passwordHash) {
        return new SysUser(id, username, passwordHash, roles, enabled);
    }
}
