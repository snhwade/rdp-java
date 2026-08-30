package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** rule_v2 表只读 Mapper（试运行加载目标定义用，R5.2）。 */
@Mapper
public interface RuleV2SampleMapper extends BaseMapper<RuleV2SamplePO> {
}
