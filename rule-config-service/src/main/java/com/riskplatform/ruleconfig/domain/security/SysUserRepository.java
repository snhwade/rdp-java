package com.riskplatform.ruleconfig.domain.security;

import java.util.List;
import java.util.Optional;

/** 系统用户仓储端口（S10）。 */
public interface SysUserRepository {

    SysUser save(SysUser user);

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<SysUser> findAll();

    Optional<SysUser> findById(Long id);

    SysUser update(SysUser user);
}
