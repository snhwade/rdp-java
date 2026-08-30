package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** risk_order 表只读 Mapper（试运行取样用，R5.1）。 */
@Mapper
public interface RiskOrderSampleMapper extends BaseMapper<RiskOrderSamplePO> {
}
