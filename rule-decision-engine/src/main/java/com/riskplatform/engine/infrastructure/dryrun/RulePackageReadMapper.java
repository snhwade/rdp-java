package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** rule_package 表只读 Mapper（试运行加载规则包定义，R5.2）。 */
@Mapper
public interface RulePackageReadMapper extends BaseMapper<RulePackagePO> {
}
