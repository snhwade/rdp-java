package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** rule_package_rule 表只读 Mapper（试运行加载包内规则关联，R5.2）。 */
@Mapper
public interface RulePackageRuleReadMapper extends BaseMapper<RulePackageRulePO> {
}
