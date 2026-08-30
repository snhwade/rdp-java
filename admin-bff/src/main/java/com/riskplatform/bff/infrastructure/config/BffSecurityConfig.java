package com.riskplatform.bff.infrastructure.config;

import com.riskplatform.common.security.JwtAuthenticationFilter;
import com.riskplatform.common.security.JwtSecuritySupport;
import com.riskplatform.common.security.RestAccessDeniedHandler;
import com.riskplatform.common.security.RestAuthEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Admin BFF 安全配置（R17.1/R17.2，RBAC + JWT 资源服务器）。
 *
 * <p>BFF 既校验 JWT（防止未授权请求向下游扩散），又向下游透传 Authorization 头（由下游二次校验，
 * 纵深防御）。授权矩阵（前缀均为 {@code /bff/api/v1}）：
 * <ul>
 *   <li>放行：登录 {@code POST /bff/api/v1/auth/login}、actuator、swagger、OpenAPI；</li>
 *   <li>创建用户 {@code POST /bff/api/v1/users} 需管理员 ADMIN；</li>
 *   <li>写操作 {@code POST/PUT/DELETE /bff/api/v1/**} 需风控运营 OPERATOR / 管理员 ADMIN；</li>
 *   <li>读操作 {@code GET /bff/api/v1/**} 需任意已认证角色（OPERATOR / AUDITOR / ADMIN）；</li>
 *   <li>其余请求需认证；无状态会话；JWT 过滤器前置；未认证 401、无权限 403（结构化错误体）。</li>
 * </ul>
 */
@Configuration
@Import(JwtSecuritySupport.class)
public class BffSecurityConfig {

    @Bean
    public SecurityFilterChain bffSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthEntryPoint authEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // 登录：须最先放行（代理至 rule-config 校验账号密码）
                        .requestMatchers("/bff/api/v1/auth/**").permitAll()
                        // standalone 进程内调度：嵌入式后端登录端点
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // 创建用户需管理员
                        .requestMatchers(HttpMethod.POST, "/bff/api/v1/users").hasRole("ADMIN")
                        // 用户治理（OU1）需管理员
                        .requestMatchers(HttpMethod.PUT, "/bff/api/v1/users/**").hasRole("ADMIN")
                        // 写操作需运营/管理员
                        .requestMatchers(HttpMethod.POST, "/bff/api/v1/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/bff/api/v1/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/bff/api/v1/**").hasAnyRole("OPERATOR", "ADMIN")
                        // 读操作需任意已认证角色
                        .requestMatchers(HttpMethod.GET, "/bff/api/v1/**")
                        .hasAnyRole("OPERATOR", "AUDITOR", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
