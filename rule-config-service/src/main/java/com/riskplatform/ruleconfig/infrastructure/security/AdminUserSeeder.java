package com.riskplatform.ruleconfig.infrastructure.security;

import com.riskplatform.ruleconfig.application.security.UserService;
import com.riskplatform.ruleconfig.domain.security.SysUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内置管理员用户初始化（S10）。
 *
 * <p>启动时若无 admin 用户则创建 admin/admin123（ADMIN 角色），便于本地登录与授权测试。
 */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final SysUserRepository repository;
    private final UserService userService;

    public AdminUserSeeder(SysUserRepository repository, UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (!repository.existsByUsername("admin")) {
            userService.createUser("admin", "admin123", List.of("ADMIN"));
            log.info("已初始化内置管理员用户 admin（角色 ADMIN）");
        }
    }
}
