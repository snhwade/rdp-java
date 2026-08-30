package com.riskplatform.screening.infrastructure;

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
 * 筛查服务安全配置（R17.1/R17.2，RBAC + JWT 资源服务器）。
 *
 * <p>授权矩阵：
 * <ul>
 *   <li>放行：actuator、swagger、OpenAPI；</li>
 *   <li>放行：{@code POST /api/v1/screening} 名称筛查——决策网关运行时编排调用（服务间链路）；
 *       {@code GET /api/v1/lists/check} 黑白名单命中判定——同为编排调用；</li>
 *   <li>{@code PUT /api/v1/screening/threshold} 阈值配置（写）需风控运营 OPERATOR / 管理员 ADMIN；</li>
 *   <li>名单记录写操作 {@code POST/PUT/DELETE /api/v1/lists/**} 需 OPERATOR / ADMIN；</li>
 *   <li>名单查询 {@code GET /api/v1/lists/**} 需任意已认证角色；</li>
 *   <li>其余 {@code /api/v1/**} 需认证；</li>
 *   <li>无状态会话；JWT 过滤器前置；未认证 401、无权限 403（结构化错误体）。</li>
 * </ul>
 */
@Configuration
@Import(JwtSecuritySupport.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain screeningSecurityFilterChain(
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
                        // 名称筛查与名单命中判定：网关运行时编排调用（服务间链路），放行
                        .requestMatchers(HttpMethod.POST, "/api/v1/screening").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/lists/check").permitAll()
                        // 阈值配置（写）需运营/管理员
                        .requestMatchers(HttpMethod.PUT, "/api/v1/screening/threshold")
                        .hasAnyRole("OPERATOR", "ADMIN")
                        // 名单记录写操作需运营/管理员
                        .requestMatchers(HttpMethod.POST, "/api/v1/lists/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/lists/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/lists/**").hasAnyRole("OPERATOR", "ADMIN")
                        // 名单查询需任意已认证角色
                        .requestMatchers(HttpMethod.GET, "/api/v1/lists/**")
                        .hasAnyRole("OPERATOR", "AUDITOR", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
