package com.riskplatform.rating.infrastructure;

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
 * 商户评级服务安全配置（R17.1/R17.2，RBAC + JWT 资源服务器）。
 *
 * <p>评级计算与查询均经 Admin BFF 透传 JWT 访问（非服务间运行时链路）。授权矩阵：
 * <ul>
 *   <li>放行：actuator、swagger、OpenAPI；</li>
 *   <li>{@code POST /api/v1/merchants/&#42;&#47;rating} 触发评级计算（写）需风控运营 OPERATOR / 管理员 ADMIN；</li>
 *   <li>{@code GET /api/v1/merchants/&#42;&#47;rating} 评级查询需任意已认证角色
 *       （OPERATOR / AUDITOR / ADMIN）；</li>
 *   <li>其余 {@code /api/v1/**} 需认证；</li>
 *   <li>无状态会话；JWT 过滤器前置；未认证 401、无权限 403（结构化错误体）。</li>
 * </ul>
 */
@Configuration
@Import(JwtSecuritySupport.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain merchantRatingSecurityFilterChain(
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
                        // 触发评级计算（写）需运营/管理员
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchants/*/rating")
                        .hasAnyRole("OPERATOR", "ADMIN")
                        // 评级查询需任意已认证角色
                        .requestMatchers(HttpMethod.GET, "/api/v1/merchants/*/rating")
                        .hasAnyRole("OPERATOR", "AUDITOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/merchant-ratings")
                        .hasAnyRole("OPERATOR", "AUDITOR", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
