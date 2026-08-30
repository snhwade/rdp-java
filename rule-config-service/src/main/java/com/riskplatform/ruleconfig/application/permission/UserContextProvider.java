package com.riskplatform.ruleconfig.application.permission;

/**
 * 当前登录用户数据权限上下文来源端口（R11.3）。
 *
 * <p>由基础设施层从 {@code SecurityContext}（JWT 主体）解析用户的机构与场景。
 * 当前 JWT 载荷仅含 username 与 roles（见 {@code JwtService}），尚不含机构/场景声明，
 * 因此默认实现先返回「超级管理员全可见」上下文（见 {@code SecurityContextUserContextProvider}），
 * 待后续为 JWT 扩展 orgPath/scenarios 声明或接入用户-机构/用户-场景绑定后再收紧。
 */
public interface UserContextProvider {

    /** 返回当前请求用户的数据权限上下文。 */
    UserDataContext currentUser();
}
