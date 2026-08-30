package com.riskplatform.ruleconfig.application.org;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.org.Org;
import com.riskplatform.ruleconfig.domain.org.OrgRepository;
import com.riskplatform.ruleconfig.domain.org.OrgScope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 机构应用服务（R11.1/R11.2）。
 *
 * <p>职责：
 * <ul>
 *   <li>创建机构：校验 code 唯一与父机构存在，入库后计算并回填物化路径 path（R11.2）；</li>
 *   <li>更新机构名称、启停状态；</li>
 *   <li>列表查询（用于树形装配）；</li>
 *   <li>提供「含下级」范围判定：返回某机构含下级范围内的全部机构（R11.2/R11.4）。</li>
 * </ul>
 *
 * <p>用 {@code @Service} 自注册为 Bean（组件扫描），避免改动共享装配类。
 */
@Service
public class OrgAppService {

    private final OrgRepository repository;

    public OrgAppService(OrgRepository repository) {
        this.repository = repository;
    }

    /** 创建机构（R11.1/R11.2）。 */
    public Org create(String code, String name, Long parentId) {
        Org org = Org.create(code, name, parentId); // 校验 name/code
        if (repository.existsByCode(code)) {
            throw BizException.duplicate("机构 code 已存在: " + code);
        }
        if (parentId != null) {
            Org parent = repository.findById(parentId)
                    .orElseThrow(() -> BizException.notFound("父机构不存在: " + parentId));
            if (!parent.isEnabled()) {
                throw BizException.invalidState("父机构已禁用，无法在其下创建子机构: " + parentId);
            }
        }
        // save 内部完成：入库取自增 id -> 回填 path -> 更新
        return repository.save(org);
    }

    /** 更新机构名称（R11.1）。 */
    public Org rename(Long id, String name) {
        Org org = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("机构不存在: " + id));
        org.rename(name);
        repository.update(org);
        return org;
    }

    /** 设置启用/禁用状态。 */
    public Org setStatus(Long id, boolean enabled) {
        Org org = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("机构不存在: " + id));
        if (enabled) {
            org.enable();
        } else {
            org.disable();
        }
        repository.update(org);
        return org;
    }

    /** 按 id 查询。 */
    public Org get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("机构不存在: " + id));
    }

    /** 列表查询（R11.1）。 */
    public List<Org> list() {
        return repository.findAll();
    }

    /**
     * 返回「机构 A 含下级」范围内的全部机构（R11.2/R11.4）。
     *
     * @param orgAId 机构 A 的 id
     */
    public List<Org> listWithinSubtree(Long orgAId) {
        Org orgA = get(orgAId);
        return repository.findByPathPrefix(orgA.getPath());
    }

    /**
     * 判定机构 X 是否落入「机构 A」的适用范围（R11.2/R11.4）。
     *
     * @param orgXId     机构 X 的 id
     * @param orgAId     机构 A 的 id
     * @param includeSub true=含下级（path 前缀匹配），false=不含下级（同 id）
     */
    public boolean isApplicable(Long orgXId, Long orgAId, boolean includeSub) {
        Org orgX = get(orgXId);
        Org orgA = get(orgAId);
        return OrgScope.applies(orgX, orgA, includeSub);
    }
}
