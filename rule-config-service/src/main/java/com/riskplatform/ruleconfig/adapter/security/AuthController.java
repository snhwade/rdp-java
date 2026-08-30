package com.riskplatform.ruleconfig.adapter.security;

import com.riskplatform.ruleconfig.application.security.UserService;
import com.riskplatform.ruleconfig.domain.security.SysUser;
import com.riskplatform.ruleconfig.infrastructure.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证 REST 适配器（S10）。
 *
 * <p>{@code POST /api/v1/auth/login}：用户名+密码校验通过后签发 JWT。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginView login(@RequestBody LoginRequest req) {
        SysUser user = userService.authenticate(req.username(), req.password());
        String token = jwtService.issue(user.username(), user.roles());
        return new LoginView(token, user.username(), user.roles());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginView(String token, String username, List<String> roles) {
    }
}
