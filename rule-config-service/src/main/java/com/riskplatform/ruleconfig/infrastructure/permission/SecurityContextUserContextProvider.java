package com.riskplatform.ruleconfig.infrastructure.permission;

import com.riskplatform.ruleconfig.application.permission.UserContextProvider;
import com.riskplatform.ruleconfig.application.permission.UserDataContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring Security {@code SecurityContext} 的用户上下文默认实现（R11.3）。
 *
 * <p><b>当前口子说明：</b>现有 JWT 载荷仅含 username 与 roles（见
 * {@code com.riskplatform.ruleconfig.infrastructure.security.JwtService}），尚未携带
 * 用户所在机构（orgPath）与用户场景（scenarios）声明。因此本默认实现暂将所有已认证用户
 * 视为<b>超级管理员（全可见）</b>，使数据权限过滤在上下文补齐前保持「不收紧、不误删」，
 * 不阻断既有功能。
 *
 * <p><b>后续收紧路径（任选其一）：</b>
 * <ol>
 *   <li>扩展 JWT 声明：签发 token 时加入 {@code orgPath} 与 {@code scenarios} 声明，
 *       本类改为从 {@code Authentication} 的 details/claims 读取并构造普通用户上下文；</li>
 *   <li>或按 username 从用户-机构、用户-场景绑定表查询装配上下文（需新增绑定表与仓储）。</li>
 * </ol>
 * 两种方式均只需替换本实现，应用层 {@code DataPermissionFilter} 与领域判定无需改动。
 *
 * <p>用 {@code @Component} 组件扫描自注册，避免改动共享 SecurityConfig/AppServiceConfig。
 */
@Component
public class SecurityContextUserContextProvider implements UserContextProvider {

    @Override
    public UserDataContext currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.getName() != null) ? auth.getName() : "anonymous";
        // 口子：JWT 暂无机构/场景声明，默认全可见。补齐声明后改为构造普通用户上下文并据此过滤。
        return UserDataContext.superAdmin(username);
    }
}
