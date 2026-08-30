package com.riskplatform.indicator.infrastructure.config;

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
 * 指标存储服务安全配置（R17.1/R17.2，RBAC + JWT 资源服务器）。
 *
 * <p>授权矩阵：
 * <ul>
 *   <li>放行：actuator、swagger、OpenAPI；</li>
 *   <li>放行：{@code GET/POST /api/v1/indicators/**} 指标读写——读由规则/决策引擎在决策链路上
 *       高频调用（≤50ms 目标），写由 Flink 累计作业与 AI 训练服务调用，均为服务间运行时链路
 *       （不携带管理前端 JWT），若强制鉴权将阻断核心决策与指标累计；</li>
 *   <li>其余 {@code /api/v1/**} 需认证；</li>
 *   <li>无状态会话；JWT 过滤器前置；未认证 401、无权限 403（结构化错误体）。</li>
 * </ul>
 */
@Configuration
@Import(JwtSecuritySupport.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain indicatorSecurityFilterChain(
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
                        // 指标读写：决策引擎/Flink/AI 服务间运行时链路，放行
                        .requestMatchers(HttpMethod.GET, "/api/v1/indicators/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/indicators/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/accumulate/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
