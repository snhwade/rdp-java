package com.riskplatform.ruleconfig.infrastructure.rulepackage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * rule_package_rule 表 MyBatis-Plus Mapper。
 */
@Mapper
public interface RulePackageRuleMapper extends BaseMapper<RulePackageRulePO> {

    /** 查询某规则包关联的规则 id 列表（按包内优先级降序，数值越大优先级越高）。 */
    @Select("SELECT rule_v2_id FROM rule_package_rule WHERE rule_package_id = #{rulePackageId} "
            + "ORDER BY priority DESC, rule_v2_id ASC")
    List<Long> selectRuleIds(@Param("rulePackageId") Long rulePackageId);
}
