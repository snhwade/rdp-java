package com.riskplatform.ruleconfig.infrastructure.indicator;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** indicator_definition 表 Mapper。 */
@Mapper
public interface IndicatorMapper extends BaseMapper<IndicatorPO> {

    /**
     * 查询引用了指定指标 refName 的启用规则标识（R7.6）。
     * 通过规则表达式包含 refName 进行匹配（启用状态）。
     */
    @Select("SELECT id FROM rule_v2 WHERE status IN ('ONLINE', 'TRIAL_RUN') "
            + "AND (compiled_expr LIKE CONCAT('%', #{refName}, '%') "
            + "OR condition_json LIKE CONCAT('%', #{refName}, '%'))")
    List<Long> findReferencingRuleIds(@Param("refName") String refName);
}
