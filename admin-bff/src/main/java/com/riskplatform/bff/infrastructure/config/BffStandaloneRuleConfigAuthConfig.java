package com.riskplatform.bff.infrastructure.config;

import com.riskplatform.ruleconfig.application.security.UserService;
import com.riskplatform.ruleconfig.domain.security.SysUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * standalone BFF：装配嵌入式 rule-config 登录所需 Bean，不引入独立 {@code SecurityFilterChain}。
 */
@Configuration
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
public class BffStandaloneRuleConfigAuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserService userService(SysUserRepository repository, PasswordEncoder passwordEncoder) {
        return new UserService(repository, passwordEncoder);
    }
}
