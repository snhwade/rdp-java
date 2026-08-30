package com.riskplatform.gateway.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.common.error.ErrorResponse;
import com.riskplatform.gateway.infrastructure.config.RiskEventApiKeyProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 事中决策入口 API Key 校验（可选）。
 *
 * <p>仅拦截 {@code POST /api/v1/risk-events}；密钥未配置时不注册本过滤器。
 */
@Component
public class RiskEventApiKeyFilter extends OncePerRequestFilter {

    private final RiskEventApiKeyProperties properties;
    private final ObjectMapper objectMapper;

    public RiskEventApiKeyFilter(RiskEventApiKeyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled() || !requiresApiKey(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String provided = request.getHeader(properties.getHeader());
        if (properties.getApiKey().equals(provided)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = ErrorResponse.of(
                CommonErrorCode.UNAUTHORIZED.code(),
                CommonErrorCode.UNAUTHORIZED.defaultMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private boolean requiresApiKey(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && "/api/v1/risk-events".equals(request.getRequestURI());
    }
}
