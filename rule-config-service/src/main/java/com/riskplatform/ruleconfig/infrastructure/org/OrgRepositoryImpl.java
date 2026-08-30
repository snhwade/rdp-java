package com.riskplatform.ruleconfig.infrastructure.org;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.org.Org;
import com.riskplatform.ruleconfig.domain.org.OrgRepository;
import com.riskplatform.ruleconfig.domain.org.OrgStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Org 仓储 MyBatis-Plus 实现（R11）。
 *
 * <p>保存流程（物化路径依赖自增 id）：
 * <ol>
 *   <li>先以空 path 占位插入，取得自增 id；</li>
 *   <li>查父机构 path（根机构为 null），调用领域对象 {@link Org#assignIdAndPath} 计算 path；</li>
 *   <li>回填 path 更新一次。</li>
 * </ol>
 */
@Repository
public class OrgRepositoryImpl implements OrgRepository {

    private final OrgMapper mapper;

    public OrgRepositoryImpl(OrgMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Org save(Org org) {
        // 1) 计算父 path（根机构为 null）
        String parentPath = null;
        if (org.getParentId() != null) {
            OrgPO parent = mapper.selectById(org.getParentId());
            if (parent == null) {
                throw BizException.notFound("父机构不存在: " + org.getParentId());
            }
            parentPath = parent.getPath();
        }
        // 2) 占位插入取自增 id（path 暂以临时非空值占位，满足 NOT NULL 约束）
        OrgPO po = toNewPO(org);
        po.setPath("/");
        mapper.insert(po);
        // 3) 领域对象按 id 计算物化路径
        org.assignIdAndPath(po.getId(), parentPath);
        // 4) 回填 path 更新
        po.setPath(org.getPath());
        mapper.updateById(po);
        return org;
    }

    @Override
    public void update(Org org) {
        OrgPO po = new OrgPO();
        po.setId(org.getId());
        po.setName(org.getName());
        po.setStatus(org.getStatus().name());
        mapper.updateById(po);
    }

    @Override
    public Optional<Org> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Org> findByCode(String code) {
        OrgPO po = mapper.selectOne(new LambdaQueryWrapper<OrgPO>().eq(OrgPO::getCode, code));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return mapper.exists(new LambdaQueryWrapper<OrgPO>().eq(OrgPO::getCode, code));
    }

    @Override
    public boolean existsByParentId(Long parentId) {
        return mapper.exists(new LambdaQueryWrapper<OrgPO>().eq(OrgPO::getParentId, parentId));
    }

    @Override
    public List<Org> findAll() {
        return mapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Org> findByPathPrefix(String orgAPath) {
        if (orgAPath == null || orgAPath.isEmpty()) {
            return List.of();
        }
        // path LIKE 'orgAPath%'，含机构 A 自身及全部下级（R11.2/R11.4）
        List<OrgPO> list = mapper.selectList(new LambdaQueryWrapper<OrgPO>()
                .likeRight(OrgPO::getPath, orgAPath));
        return list.stream().map(this::toDomain).toList();
    }

    private OrgPO toNewPO(Org org) {
        OrgPO po = new OrgPO();
        po.setCode(org.getCode());
        po.setName(org.getName());
        po.setParentId(org.getParentId());
        po.setStatus(org.getStatus().name());
        return po;
    }

    private Org toDomain(OrgPO po) {
        OrgStatus status = "DISABLED".equalsIgnoreCase(po.getStatus())
                ? OrgStatus.DISABLED : OrgStatus.ENABLED;
        return Org.rehydrate(po.getId(), po.getCode(), po.getName(),
                po.getParentId(), po.getPath(), status);
    }
}
