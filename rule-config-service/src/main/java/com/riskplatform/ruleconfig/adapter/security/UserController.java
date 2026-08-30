package com.riskplatform.ruleconfig.adapter.security;

import com.riskplatform.ruleconfig.application.security.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理 REST 适配器（S10 / OU1）。
 *
 * <ul>
 *   <li>POST /api/v1/users 创建用户（需 ADMIN）</li>
 *   <li>GET  /api/v1/users 用户列表（需认证）</li>
 *   <li>PUT  /api/v1/users/{id}/enabled 启停（需 ADMIN）</li>
 *   <li>PUT  /api/v1/users/{id}/roles 改角色（需 ADMIN）</li>
 *   <li>PUT  /api/v1/users/{id}/reset-password 重置密码（需 ADMIN）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public UserView create(@RequestBody CreateUserRequest req) {
        var u = userService.createUser(req.username(), req.password(), req.roles());
        return UserView.from(u);
    }

    @GetMapping
    public List<UserView> list() {
        return userService.listUsers().stream().map(UserView::from).toList();
    }

    @PutMapping("/{id}/enabled")
    public UserView setEnabled(@PathVariable("id") Long id, @RequestBody SetEnabledRequest req) {
        return UserView.from(userService.setEnabled(id, req.enabled()));
    }

    @PutMapping("/{id}/roles")
    public UserView updateRoles(@PathVariable("id") Long id, @RequestBody UpdateRolesRequest req) {
        return UserView.from(userService.updateRoles(id, req.roles()));
    }

    @PutMapping("/{id}/reset-password")
    public UserView resetPassword(@PathVariable("id") Long id, @RequestBody ResetPasswordRequest req) {
        return UserView.from(userService.resetPassword(id, req.password()));
    }

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotEmpty List<String> roles) {
    }

    public record SetEnabledRequest(@NotNull Boolean enabled) {
    }

    public record UpdateRolesRequest(@NotEmpty List<String> roles) {
    }

    public record ResetPasswordRequest(@NotBlank String password) {
    }

    public record UserView(Long id, String username, List<String> roles, boolean enabled) {
        static UserView from(com.riskplatform.ruleconfig.domain.security.SysUser u) {
            return new UserView(u.id(), u.username(), u.roles(), u.enabled());
        }
    }
}
