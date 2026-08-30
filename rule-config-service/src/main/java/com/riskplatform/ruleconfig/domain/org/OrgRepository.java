package com.riskplatform.ruleconfig.domain.org;

import java.util.List;
import java.util.Optional;

/**
 * 机构仓储端口（R11）。由基础设施层用 MyBatis-Plus 持久化到 org 表。
 */
public interface OrgRepository {

    /**
     * 保存新机构：先入库取得自增 id，再回填物化路径 path 并更新（R11.2）。
     * 返回带 id 与 path 的实体。
     */
    Org save(Org org);

    /** 更新机构（名称/状态等，不改层级与 path）。 */
    void update(Org org);

    /** 按 id 查询。 */
    Optional<Org> findById(Long id);

    /** 按 code 查询。 */
    Optional<Org> findByCode(String code);

    /** code 是否已存在。 */
    boolean existsByCode(String code);

    /** 是否存在以指定父机构为父的子机构（用于删除前校验）。 */
    boolean existsByParentId(Long parentId);

    /** 查询全部机构（无则空列表）。 */
    List<Org> findAll();

    /**
     * 查询「机构 A 含下级」范围内的全部机构：path 以 orgAPath 为前缀（R11.2/R11.4）。
     */
    List<Org> findByPathPrefix(String orgAPath);
}
