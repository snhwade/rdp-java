package com.riskplatform.ruleconfig.application.security;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.ruleconfig.application.permission.UserContextProvider;
import com.riskplatform.ruleconfig.domain.security.SysUser;
import com.riskplatform.ruleconfig.domain.security.SysUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户与登录应用服务（S10 / OU1）。
 *
 * <p>用户创建（BCrypt 哈希密码）、启停、改角色、重置密码（管理员）。
 */
public class UserService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "OPERATOR", "AUDITOR");

    private final SysUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UserContextProvider userContextProvider;

    public UserService(SysUserRepository repository, PasswordEncoder passwordEncoder) {
        this(repository, passwordEncoder, null);
    }

    public UserService(SysUserRepository repository,
                       PasswordEncoder passwordEncoder,
                       UserContextProvider userContextProvider) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.userContextProvider = userContextProvider;
    }

    public SysUser createUser(String username, String rawPassword, List<String> roles) {
        if (repository.existsByUsername(username)) {
            throw BizException.duplicate("用户名已存在: " + username);
        }
        validateRoles(roles);
        SysUser user = SysUser.create(username, passwordEncoder.encode(rawPassword), roles);
        return repository.save(user);
    }

    public List<SysUser> listUsers() {
        return repository.findAll();
    }

    public SysUser setEnabled(Long id, boolean enabled) {
        SysUser user = requireUser(id);
        if (!enabled) {
            assertNotSelf(user, "不能禁用当前登录账号");
            assertEnabledAdminSurvivalExcluding(user);
        }
        return repository.update(user.withEnabled(enabled));
    }

    public SysUser updateRoles(Long id, List<String> roles) {
        validateRoles(roles);
        SysUser user = requireUser(id);
        List<String> normalized = roles.stream().map(r -> r.trim().toUpperCase()).distinct().toList();
        if (user.enabled() && user.roles().stream().anyMatch("ADMIN"::equalsIgnoreCase)
                && normalized.stream().noneMatch("ADMIN"::equals)) {
            assertEnabledAdminSurvivalExcluding(user);
        }
        return repository.update(user.withRoles(normalized));
    }

    public SysUser resetPassword(Long id, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BizException(CommonErrorCode.INVALID_FIELD, "密码不能为空");
        }
        SysUser user = requireUser(id);
        return repository.update(user.withPasswordHash(passwordEncoder.encode(rawPassword)));
    }

    /** 登录校验：用户名存在、启用、密码匹配；成功返回用户（含角色）。 */
    public SysUser authenticate(String username, String rawPassword) {
        SysUser user = repository.findByUsername(username)
                .orElseThrow(() -> new BizException(CommonErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!user.enabled() || !passwordEncoder.matches(rawPassword, user.passwordHash())) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return user;
    }

    private SysUser requireUser(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("用户不存在: id=" + id));
    }

    private void validateRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BizException(CommonErrorCode.INVALID_FIELD, "至少一个角色");
        }
        Set<String> seen = new HashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                throw new BizException(CommonErrorCode.INVALID_FIELD, "角色不能为空");
            }
            String normalized = role.trim().toUpperCase();
            if (!ALLOWED_ROLES.contains(normalized)) {
                throw new BizException(CommonErrorCode.INVALID_FIELD, "非法角色: " + role);
            }
            seen.add(normalized);
        }
    }

    private void assertNotSelf(SysUser target, String message) {
        if (userContextProvider == null) {
            return;
        }
        try {
            var current = userContextProvider.currentUser();
            if (current != null && target.username().equals(current.username())) {
                throw new BizException(CommonErrorCode.INVALID_FIELD, message);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ignored) {
            // 无安全上下文时不阻断
        }
    }

    /** 禁用或移除 ADMIN 后，须至少保留一名其他启用的管理员。 */
    private void assertEnabledAdminSurvivalExcluding(SysUser target) {
        if (!target.roles().stream().anyMatch("ADMIN"::equalsIgnoreCase)) {
            return;
        }
        long otherEnabledAdmins = repository.findAll().stream()
                .filter(u -> !u.id().equals(target.id()))
                .filter(SysUser::enabled)
                .filter(u -> u.roles().stream().anyMatch("ADMIN"::equalsIgnoreCase))
                .count();
        if (otherEnabledAdmins == 0) {
            throw new BizException(CommonErrorCode.INVALID_FIELD, "至少需保留一名启用的管理员");
        }
    }
}
