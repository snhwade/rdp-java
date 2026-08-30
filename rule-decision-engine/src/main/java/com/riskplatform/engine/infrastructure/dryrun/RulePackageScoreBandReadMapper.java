package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** rule_package_score_band 表只读 Mapper（评分模式分值区间加载，R5.4）。 */
@Mapper
public interface RulePackageScoreBandReadMapper extends BaseMapper<RulePackageScoreBandPO> {
}
