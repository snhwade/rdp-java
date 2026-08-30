package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** rule_dynamic_score 表只读 Mapper（评分模式动态分加载，R5.4）。 */
@Mapper
public interface RuleDynamicScoreMapper extends BaseMapper<RuleDynamicScorePO> {
}
