package com.riskplatform.ruleconfig.infrastructure.indicator;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 指标引用扫描 SQL（IR1）。 */
@Mapper
public interface IndicatorReferenceMapper {

    @Select("""
            SELECT DISTINCT CONCAT('规则包:',
                COALESCE(rp.code, CAST(r.rule_package_id AS CHAR), r.code),
                CASE WHEN r.code IS NOT NULL AND (rp.code IS NOT NULL OR r.rule_package_id IS NOT NULL)
                     THEN CONCAT('/', r.code) ELSE '' END)
            FROM rule_v2 r
            LEFT JOIN rule_package rp ON rp.id = r.rule_package_id
            WHERE (r.condition_json LIKE #{pattern} OR r.compiled_expr LIKE #{pattern})
            """)
    List<String> findRulePackageReferences(@Param("pattern") String pattern);

    @Select("""
            SELECT DISTINCT CONCAT('决策流:', COALESCE(name, CAST(id AS CHAR)))
            FROM decision_flow
            WHERE (nodes_json LIKE #{pattern} OR edges_json LIKE #{pattern})
            """)
    List<String> findDecisionFlowReferences(@Param("pattern") String pattern);

    @Select("""
            SELECT DISTINCT CONCAT('决策流版本:', COALESCE(df.name, CAST(v.decision_flow_id AS CHAR)),
                '/v', v.version)
            FROM decision_flow_version v
            LEFT JOIN decision_flow df ON df.id = v.decision_flow_id
            WHERE v.snapshot_json LIKE #{pattern}
            """)
    List<String> findDecisionFlowVersionReferences(@Param("pattern") String pattern);

    @Select("""
            SELECT DISTINCT CONCAT('逻辑指标:', li.ref_name)
            FROM logical_indicator_member lim
            JOIN logical_indicator li ON li.id = lim.logical_indicator_id
            WHERE lim.member_ref_name = #{refName}
            """)
    List<String> findLogicalIndicatorMemberReferences(@Param("refName") String refName);

    @Select("""
            SELECT DISTINCT CONCAT('逻辑指标:', ref_name)
            FROM logical_indicator
            WHERE combine_expression LIKE #{pattern}
            """)
    List<String> findLogicalIndicatorExpressionReferences(@Param("pattern") String pattern);
}
