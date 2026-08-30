package com.riskplatform.engine.infrastructure.config;

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
 * 规则/决策引擎安全配置（R17.1/R17.2，RBAC + JWT 资源服务器）。
 *
 * <p>授权矩阵：
 * <ul>
 *   <li>放行：actuator、swagger、OpenAPI；</li>
 *   <li>放行：{@code POST /api/v1/**&#47;evaluate} 各类决策评估入口（决策/决策表/评分卡/决策流/
 *       决策树/决策矩阵），由决策网关运行时编排调用，属服务间链路（不经管理前端 JWT）；</li>
 *   <li>{@code GET /api/v1/decisions/**}、{@code GET /api/v1/trace/**} 决策结果与执行链路查询
 *       （经 Admin BFF 透传 JWT）需任意已认证角色；</li>
 *   <li>其余 {@code /api/v1/**} 需认证；</li>
 *   <li>无状态会话；JWT 过滤器前置；未认证 401、无权限 403（结构化错误体）。</li>
 * </ul>
 */
@Configuration
@Import(JwtSecuritySupport.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain engineSecurityFilterChain(
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
                        // 决策评估入口：网关运行时编排调用（服务间链路），放行
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/decision-flows/evaluate",
                                "/api/v1/decision-flows/evaluate-trace",
                                "/api/v1/decision-flows/*/evaluate",
                                "/api/v1/rule-packages/*/evaluate",
                                "/api/v1/decision-tables/evaluate",
                                "/api/v1/scorecards/evaluate",
                                "/api/v1/decision-trees/evaluate",
                                "/api/v1/decision-matrices/evaluate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/decision-flows/bindings").permitAll()
                        // 决策结果 / 执行链路查询：管理前端经 BFF 透传 JWT，任意已认证角色可读
                        .requestMatchers(HttpMethod.GET, "/api/v1/decisions/**", "/api/v1/trace/**")
                        .hasAnyRole("OPERATOR", "AUDITOR", "ADMIN")
                        .requestMatchers("/api/v1/config-cache", "/api/v1/config-cache/**")
                        .hasAnyRole("ADMIN", "OPERATOR")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
