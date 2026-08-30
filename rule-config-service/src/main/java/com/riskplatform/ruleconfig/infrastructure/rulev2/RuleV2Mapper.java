package com.riskplatform.ruleconfig.infrastructure.rulev2;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * rule_v2 表 MyBatis-Plus Mapper。
 */
@Mapper
public interface RuleV2Mapper extends BaseMapper<RuleV2PO> {

    /**
     * 按 {@code status} 分组聚合某规则包下的规则条数（R6.6 三态计数）。
     *
     * <p>返回每个状态值及其计数行，形如 {@code [{status:'ONLINE', cnt:3}, ...]}。
     * 状态值以字符串承载（ONLINE/TRIAL_RUN/OFFLINE），与状态枚举解耦，便于跨任务并行演进。
     */
    @Select("SELECT status AS status, COUNT(*) AS cnt FROM rule_v2 "
            + "WHERE rule_package_id = #{rulePackageId} GROUP BY status")
    List<Map<String, Object>> countByStatusForPackage(@Param("rulePackageId") Long rulePackageId);

    /**
     * 一次性按 {@code rule_package_id + status} 分组聚合多个规则包的规则三态计数（R6.6/R6.1）。
     *
     * <p>用于卡片墙批量加载，单条 GROUP BY 查询替代逐包查询，避免 N+1。返回行形如
     * {@code [{rulePackageId:1, status:'ONLINE', cnt:3}, ...]}；{@code rulePackageIds} 为空时返回空列表。
     */
    @Select("<script>"
            + "SELECT rule_package_id AS rulePackageId, status AS status, COUNT(*) AS cnt FROM rule_v2 "
            + "WHERE rule_package_id IN "
            + "<foreach collection='rulePackageIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach> "
            + "GROUP BY rule_package_id, status"
            + "</script>")
    List<Map<String, Object>> countByStatusForPackages(@Param("rulePackageIds") List<Long> rulePackageIds);

    /** 直接更新规则状态（按状态字符串，与枚举解耦，用于批量上线/试运行/下线）。 */
    @Update("UPDATE rule_v2 SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 迁移规则归属规则包（批量移动）。 */
    @Update("UPDATE rule_v2 SET rule_package_id = #{targetRulePackageId} WHERE id = #{id}")
    int updateRulePackage(@Param("id") Long id, @Param("targetRulePackageId") Long targetRulePackageId);

    /** 更新规则适用机构（批量编辑机构）。 */
    @Update("UPDATE rule_v2 SET applicable_org_id = #{applicableOrgId}, include_sub_org = #{includeSubOrg} "
            + "WHERE id = #{id}")
    int updateApplicableOrg(@Param("id") Long id, @Param("applicableOrgId") Long applicableOrgId,
                            @Param("includeSubOrg") Integer includeSubOrg);
}
