package com.riskplatform.screening.infrastructure.listmgmt;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 名单库引用扫描 SQL（L1）。 */
@Mapper
public interface ListLibraryReferenceMapper {

    @Select("""
            SELECT DISTINCT CONCAT('规则包:',
                COALESCE(rp.code, CAST(r.rule_package_id AS CHAR), r.code),
                CASE WHEN r.code IS NOT NULL AND (rp.code IS NOT NULL OR r.rule_package_id IS NOT NULL)
                     THEN CONCAT('/', r.code) ELSE '' END)
            FROM rule_v2 r
            LEFT JOIN rule_package rp ON rp.id = r.rule_package_id
            WHERE (r.condition_json LIKE #{p1} OR r.condition_json LIKE #{p2} OR r.condition_json LIKE #{p3}
                OR r.compiled_expr LIKE #{p1} OR r.compiled_expr LIKE #{p2} OR r.compiled_expr LIKE #{p3})
            """)
    List<String> findRulePackageReferences(@Param("p1") String p1,
                                           @Param("p2") String p2,
                                           @Param("p3") String p3);

    @Select("""
            SELECT DISTINCT CONCAT('决策流:', COALESCE(name, CAST(id AS CHAR)))
            FROM decision_flow
            WHERE (nodes_json LIKE #{p1} OR nodes_json LIKE #{p2} OR nodes_json LIKE #{p3}
                OR edges_json LIKE #{p1} OR edges_json LIKE #{p2} OR edges_json LIKE #{p3})
            """)
    List<String> findDecisionFlowReferences(@Param("p1") String p1,
                                            @Param("p2") String p2,
                                            @Param("p3") String p3);

    @Select("""
            SELECT DISTINCT CONCAT('决策流版本:', COALESCE(df.name, CAST(v.decision_flow_id AS CHAR)),
                '/v', v.version)
            FROM decision_flow_version v
            LEFT JOIN decision_flow df ON df.id = v.decision_flow_id
            WHERE (v.snapshot_json LIKE #{p1} OR v.snapshot_json LIKE #{p2} OR v.snapshot_json LIKE #{p3})
            """)
    List<String> findDecisionFlowVersionReferences(@Param("p1") String p1,
                                                   @Param("p2") String p2,
                                                   @Param("p3") String p3);
}
