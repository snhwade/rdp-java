package com.riskplatform.ruleconfig.infrastructure.reference;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 跨子域引用/依赖 COUNT 与存在性查询（字段血缘、事件字段、事件依赖、参数引用解析）。
 */
@Mapper
public interface CrossDomainReferenceMapper {

    @Select("SELECT COUNT(1) FROM event_field WHERE field_id = #{fieldId}")
    Integer countEventFieldByFieldId(@Param("fieldId") Long fieldId);

    @Select("""
            SELECT COUNT(1) FROM rule_v2
            WHERE condition_json LIKE #{pattern} OR compiled_expr LIKE #{pattern}
            """)
    Integer countRuleByFieldPattern(@Param("pattern") String pattern);

    @Select("""
            SELECT COUNT(1) FROM decision_flow
            WHERE nodes_json LIKE #{pattern} OR edges_json LIKE #{pattern}
            """)
    Integer countDecisionFlowByFieldPattern(@Param("pattern") String pattern);

    @Select("""
            SELECT COUNT(1) FROM indicator_definition
            WHERE dimensions LIKE #{pattern} OR acc_script LIKE #{pattern}
            """)
    Integer countIndicatorByFieldPattern(@Param("pattern") String pattern);

    @Select("SELECT code FROM field_library WHERE id = #{fieldId}")
    String findFieldCodeById(@Param("fieldId") Long fieldId);

    @Select("""
            SELECT COUNT(1) FROM rule_v2
            WHERE event_type_code = #{eventCode} AND condition_json LIKE #{pattern}
            """)
    Integer countRuleByEventAndFieldPattern(@Param("eventCode") String eventCode,
                                            @Param("pattern") String pattern);

    @Select("""
            SELECT COUNT(1) FROM rating_item ri
            JOIN rating_model rm ON rm.id = ri.rating_model_id
            WHERE rm.event_type_code = #{eventCode} AND ri.condition_expr LIKE #{pattern}
            """)
    Integer countRatingByEventAndFieldPattern(@Param("eventCode") String eventCode,
                                              @Param("pattern") String pattern);

    @Select("SELECT COUNT(1) FROM event_field WHERE event_type_code = #{eventCode}")
    Integer countEventFieldByEventCode(@Param("eventCode") String eventCode);

    @Select("SELECT COUNT(1) FROM rule_package_event WHERE event_type_code = #{eventCode}")
    Integer countRulePackageEventByEventCode(@Param("eventCode") String eventCode);

    @Select("SELECT COUNT(1) FROM decision_flow WHERE event_type_code = #{eventCode}")
    Integer countDecisionFlowByEventCode(@Param("eventCode") String eventCode);

    @Select("SELECT COUNT(1) FROM rating_model WHERE event_type_code = #{eventCode}")
    Integer countRatingModelByEventCode(@Param("eventCode") String eventCode);

    @Select("""
            SELECT COUNT(1) FROM event_field ef
            JOIN field_library fl ON fl.id = ef.field_id
            WHERE ef.event_type_code = #{eventCode} AND fl.code = #{fieldCode}
            """)
    Integer countEventFieldByEventAndFieldCode(@Param("eventCode") String eventCode,
                                               @Param("fieldCode") String fieldCode);

    @Select("""
            SELECT CASE WHEN EXISTS (
              SELECT 1 FROM decision_flow WHERE event_type_code = #{eventCode} AND status = 'ENABLED'
            ) OR EXISTS (
              SELECT 1 FROM rule_package_event rpe
              JOIN rule_package rp ON rp.id = rpe.rule_package_id
              WHERE rpe.event_type_code = #{eventCode} AND rp.status = 'ENABLED'
            ) THEN 1 ELSE 0 END
            """)
    Integer isEventExecutable(@Param("eventCode") String eventCode);
}
