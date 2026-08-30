package com.riskplatform.ruleconfig.infrastructure.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.security.SysUser;
import com.riskplatform.ruleconfig.domain.security.SysUserRepository;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 系统用户仓储 MyBatis-Plus 实现（S10）。 */
@Repository
public class SysUserRepositoryImpl implements SysUserRepository {

    private final SysUserMapper mapper;

    public SysUserRepositoryImpl(SysUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SysUser save(SysUser user) {
        SysUserPO po = new SysUserPO();
        po.setId(user.id());
        po.setUsername(user.username());
        po.setPasswordHash(user.passwordHash());
        po.setRoles(String.join(",", user.roles()));
        po.setEnabled(user.enabled() ? 1 : 0);
        mapper.insert(po);
        return toDomain(po);
    }

    @Override
    public Optional<SysUser> findByUsername(String username) {
        SysUserPO po = mapper.selectOne(new LambdaQueryWrapper<SysUserPO>()
                .eq(SysUserPO::getUsername, username));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return mapper.exists(new LambdaQueryWrapper<SysUserPO>().eq(SysUserPO::getUsername, username));
    }

    @Override
    public List<SysUser> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<>()).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<SysUser> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public SysUser update(SysUser user) {
        SysUserPO po = new SysUserPO();
        po.setId(user.id());
        po.setUsername(user.username());
        po.setPasswordHash(user.passwordHash());
        po.setRoles(String.join(",", user.roles()));
        po.setEnabled(user.enabled() ? 1 : 0);
        mapper.updateById(po);
        return toDomain(mapper.selectById(user.id()));
    }

    private SysUser toDomain(SysUserPO po) {
        List<String> roles = po.getRoles() == null || po.getRoles().isBlank()
                ? List.of() : Arrays.stream(po.getRoles().split(",")).map(String::trim).toList();
        return new SysUser(po.getId(), po.getUsername(), po.getPasswordHash(), roles,
                po.getEnabled() == null || po.getEnabled() == 1);
    }
}
