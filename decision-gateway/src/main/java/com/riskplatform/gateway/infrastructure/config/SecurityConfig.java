package com.riskplatform.gateway.infrastructure.config;

import com.riskplatform.common.security.JwtAuthenticationFilter;
import com.riskplatform.common.security.JwtSecuritySupport;
import com.riskplatform.common.security.RestAccessDeniedHandler;
import com.riskplatform.common.security.RestAuthEntryPoint;
import com.riskplatform.gateway.infrastructure.config.RiskEventApiKeyProperties;
import com.riskplatform.gateway.infrastructure.security.RiskEventApiKeyFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 * 决策网关安全配置（R17.1/R17.2，RBAC + JWT 资源服务器）。
 *
 * <p>授权矩阵：
 * <ul>
 *   <li>放行：actuator、swagger、OpenAPI；</li>
 *   <li>放行：{@code POST /api/v1/risk-events} 事中决策受理入口；若配置了
 *       {@code security.risk-events.api-key}，则由 {@link RiskEventApiKeyFilter} 校验请求头密钥；</li>
 *   <li>{@code GET /api/v1/orders} 订单查询（经 Admin BFF 透传 JWT）需任意已认证角色
 *       （风控运营 OPERATOR / 只读审计 AUDITOR / 管理员 ADMIN）；</li>
 *   <li>其余 {@code /api/v1/**} 需认证；</li>
 *   <li>无状态会话；JWT 过滤器在用户名密码过滤器前执行；未认证 401、无权限 403（结构化错误体）。</li>
 * </ul>
 */
@Configuration
@Import(JwtSecuritySupport.class)
@EnableConfigurationProperties(RiskEventApiKeyProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain gatewaySecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthEntryPoint authEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            RiskEventApiKeyFilter riskEventApiKeyFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // 事中决策受理：运行时链路入口，放行（非管理前端接口）
                        .requestMatchers(HttpMethod.POST, "/api/v1/risk-events").permitAll()
                        // 订单查询：管理前端经 BFF 透传 JWT，任意已认证角色可读
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders",
                                "/api/v1/business-orders",
                                "/api/v1/business-orders/**")
                        .hasAnyRole("OPERATOR", "AUDITOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/decision-records",
                                "/api/v1/decision-records/**",
                                "/api/v1/engine-decision-records",
                                "/api/v1/engine-decision-records/**",
                                "/api/v1/ai-decision-records",
                                "/api/v1/ai-decision-records/**",
                                "/api/v1/agent/runtime")
                        .hasAnyRole("OPERATOR", "AUDITOR", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(riskEventApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
