package com.riskplatform.ruleconfig.infrastructure.rulepackage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * rule_package_event 表 MyBatis-Plus Mapper。
 */
@Mapper
public interface RulePackageEventMapper extends BaseMapper<RulePackageEventPO> {

    /** 查询关联了某决策事件编码的规则包 id 列表（卡片墙按事件过滤，R6.1）。 */
    @Select("SELECT rule_package_id FROM rule_package_event WHERE event_type_code = #{eventCode}")
    List<Long> selectPackageIdsByEventCode(@Param("eventCode") String eventCode);
}
