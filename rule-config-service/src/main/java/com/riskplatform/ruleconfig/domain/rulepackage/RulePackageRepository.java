package com.riskplatform.ruleconfig.domain.rulepackage;

import java.util.List;
import java.util.Optional;

/**
 * 规则包仓储端口（R1）。
 *
 * <p>领域层定义契约，基础设施层（MyBatis-Plus）实现。负责持久化规则包聚合及其关联
 * （场景/事件/分值区间）与规则关联（{@code rule_package_rule}）。
 */
public interface RulePackageRepository {

    /** 新增规则包聚合（含场景/事件/分值区间关联），回填自增 id。 */
    RulePackage save(RulePackage rulePackage);

    /** 更新规则包聚合（含关联全量替换）。 */
    void update(RulePackage rulePackage);

    /** 按主键查询，重建完整聚合。 */
    Optional<RulePackage> findById(Long id);

    /** 按触发模式 + 编码查询（对应唯一约束 uk_rule_package_mode_code，R1.4）。 */
    Optional<RulePackage> findByCodeAndTriggerMode(String code, TriggerMode triggerMode);

    /** 列出全部规则包（配置页列表查询用）。 */
    List<RulePackage> findAll();

    /**
     * 按决策事件编码列出规则包（卡片墙按事件过滤，R6.1）。
     *
     * <p>经关联表 {@code rule_package_event} 过滤；{@code eventCode} 为空时等价于 {@link #findAll()}。
     */
    List<RulePackage> findByEventCode(String eventCode);

    /**
     * 同一触发模式下是否存在同名规则包（排除自身），用于名称唯一性校验钩子（R1.4）。
     *
     * @param triggerMode 触发模式
     * @param name        规则包名称
     * @param selfId      当前规则包 id（更新场景排除自身；创建场景传 null）
     */
    boolean existsByTriggerModeAndName(TriggerMode triggerMode, String name, Long selfId);

    /** 关联规则到规则包（支持规则在多个包中，含包内优先级，R1.7/3.x）。 */
    void associateRule(Long rulePackageId, Long ruleV2Id, int priority);

    /** 解除规则与规则包的关联。 */
    void dissociateRule(Long rulePackageId, Long ruleV2Id);

    /** 查询某规则包关联的规则 id 列表（按包内优先级降序）。 */
    List<Long> findRuleIds(Long rulePackageId);
}
