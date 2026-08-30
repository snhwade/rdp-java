package com.riskplatform.ruleconfig.domain.rulev2;

import java.util.List;
import java.util.Optional;

/**
 * 结构化规则仓储端口（R2/R4）。
 *
 * <p>领域层定义契约，基础设施层（MyBatis-Plus）实现。负责持久化 {@link RuleV2} 聚合及其
 * 子表 {@code rule_dynamic_score}（动态分区间，全量替换）。
 */
public interface RuleV2Repository {

    /** 新增结构化规则聚合（含动态分区间），回填自增 id。 */
    RuleV2 save(RuleV2 rule);

    /** 更新结构化规则聚合（含动态分区间全量替换）。 */
    void update(RuleV2 rule);

    /** 按主键查询，重建完整聚合。 */
    Optional<RuleV2> findById(Long id);

    /** 列出全部结构化规则（配置页列表查询用）。 */
    List<RuleV2> findAll();

    /** 按规则包 id 列出关联规则。 */
    List<RuleV2> findByRulePackageId(Long rulePackageId);

    /**
     * 按规则包 id 列出规则列表读模型（R6.4），状态以原始字符串透出（ONLINE/TRIAL_RUN/OFFLINE）。
     *
     * <p>用于规则包详情的规则列表展示，避免经状态枚举转换丢失三态信息。
     */
    List<RuleListItem> findListItemsByRulePackageId(Long rulePackageId);

    /**
     * 是否存在相同编译表达式（compiled_expr）的其它启用规则（排除自身），
     * 用于条件重复校验钩子（R2.x 可配置开关）。
     *
     * @param compiledExpr 编译后的 Aviator 表达式
     * @param selfId       当前规则 id（更新场景排除自身；创建场景传 null）
     */
    boolean existsByCompiledExpr(String compiledExpr, Long selfId);

    /**
     * 按 {@code rulePackageId + status} 分组聚合该规则包下的规则三态计数（R6.6）。
     *
     * <p>聚合在仓储层完成（GROUP BY status），返回上线/试运行/下线条数。
     */
    RuleStatusCounts countByStatus(Long rulePackageId);

    /**
     * 一次性聚合多个规则包的三态计数（R6.6/R6.1）。
     *
     * <p>以单条 {@code GROUP BY rule_package_id, status} 查询替代逐包查询，避免 N+1；
     * 入参为空或 {@code null} 时返回空映射。返回映射中每个规则包 id 对应其三态计数，
     * 未出现于结果（即无规则）的规则包不在映射中，调用方需按需回退为 {@link RuleStatusCounts#empty()}。
     */
    java.util.Map<Long, RuleStatusCounts> countByStatusForPackages(List<Long> rulePackageIds);

    /**
     * 直接将规则状态置为给定状态字符串（ONLINE/TRIAL_RUN/OFFLINE），用于批量状态操作（R6.5）。
     *
     * <p>以字符串承载状态，避免与状态枚举演进强耦合；返回受影响行数（0 表示规则不存在）。
     */
    int updateStatus(Long ruleId, String status);

    /** 将规则迁移到目标规则包（批量移动，R6.5）。返回受影响行数。 */
    int moveToPackage(Long ruleId, Long targetRulePackageId);

    /** 更新规则适用机构（批量编辑机构，R6.5）。返回受影响行数。 */
    int updateApplicableOrg(Long ruleId, Long applicableOrgId, boolean includeSubOrg);

    /** 删除规则（批量删除，R6.5）。返回受影响行数。 */
    int deleteById(Long ruleId);

    /**
     * 复制规则到目标规则包（批量复制，R6.5）。
     *
     * <p>复制规则主体与动态分区间，赋予新编码 {@code newCode}，归属 {@code targetRulePackageId}
     * （为空时复制到源规则所在包），新规则状态置为下线（OFFLINE）。返回新规则。
     */
    RuleV2 copy(Long sourceRuleId, Long targetRulePackageId, String newCode);
}
