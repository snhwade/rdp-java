package com.riskplatform.ruleconfig.infrastructure.security;

import com.riskplatform.common.security.RestAccessDeniedHandler;
import com.riskplatform.common.security.RestAuthEntryPoint;
import com.riskplatform.ruleconfig.application.security.UserService;
import com.riskplatform.ruleconfig.domain.security.SysUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置（S10，RBAC + JWT）。
 *
 * <p>规则：
 * <ul>
 *   <li>放行：登录、actuator、swagger、OpenAPI；</li>
 *   <li>写操作（POST/PUT/DELETE /api/v1/**）需 OPERATOR 或 ADMIN；</li>
 *   <li>创建用户（/api/v1/users）需 ADMIN；</li>
 *   <li>其余 /api/v1/** 需认证（任意角色）；</li>
 *   <li>无状态会话；JWT 过滤器在用户名密码过滤器前执行；未认证 401、无权限 403（结构化错误体）。</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserService userService(SysUserRepository repository,
                                   PasswordEncoder passwordEncoder,
                                   com.riskplatform.ruleconfig.application.permission.UserContextProvider userContextProvider) {
        return new UserService(repository, passwordEncoder, userContextProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new RestAuthEntryPoint())
                        .accessDeniedHandler(new RestAccessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/actuator/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // 服务间只读加载（indicator-store 同步指标定义等）放行 GET
                        .requestMatchers(HttpMethod.GET, "/api/v1/indicator-definitions",
                                "/api/v1/indicator-definitions/**",
                                "/api/v1/event-types").permitAll()
                        // 创建用户需 ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").hasRole("ADMIN")
                        // 用户治理（OU1）需 ADMIN
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/**").hasRole("ADMIN")
                        // 写操作需 OPERATOR 或 ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/v1/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasAnyRole("OPERATOR", "ADMIN")
                        // 其余（含 GET）需认证
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
