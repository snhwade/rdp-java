package com.riskplatform.engine.integration.support;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 引擎集成测试造数/断言/清理（仅 test scope，替代 JdbcTemplate 直写 SQL）。 */
@Mapper
public interface EngineIntegrationTestDataMapper {

    // —— rating_model cleanup / seed ——

    @Delete("DELETE FROM rating_grade_band WHERE rating_model_id IN "
            + "(SELECT id FROM rating_model WHERE name LIKE #{pattern})")
    void deleteRatingGradeBandsByModelNamePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rating_item WHERE rating_model_id IN "
            + "(SELECT id FROM rating_model WHERE name LIKE #{pattern})")
    void deleteRatingItemsByModelNamePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rating_model WHERE name LIKE #{pattern}")
    void deleteRatingModelsByNamePattern(@Param("pattern") String pattern);

    @Insert("""
            INSERT INTO rating_model (name, event_type_code, execution_mode, subject, grading_mode, status, version)
            VALUES (#{name}, #{eventCode}, #{executionMode}, #{subject}, #{gradingMode}, 'ONLINE', 1)
            """)
    void insertRatingModel(@Param("name") String name,
                           @Param("eventCode") String eventCode,
                           @Param("executionMode") String executionMode,
                           @Param("subject") String subject,
                           @Param("gradingMode") String gradingMode);

    @Select("SELECT id FROM rating_model WHERE name = #{name}")
    Long findRatingModelIdByName(@Param("name") String name);

    @Insert("""
            INSERT INTO rating_grade_band (rating_model_id, min_score, max_score, grade, order_no)
            VALUES (#{modelId}, #{minScore}, #{maxScore}, #{grade}, #{orderNo})
            """)
    void insertGradeBand(@Param("modelId") long modelId,
                         @Param("minScore") BigDecimal minScore,
                         @Param("maxScore") BigDecimal maxScore,
                         @Param("grade") String grade,
                         @Param("orderNo") int orderNo);

    @Insert("INSERT INTO rating_item (rating_model_id, condition_expr, grade) VALUES (#{modelId}, #{condition}, #{grade})")
    void insertDirectRatingItem(@Param("modelId") long modelId,
                                @Param("condition") String condition,
                                @Param("grade") String grade);

    @Insert("""
            INSERT INTO rating_item (rating_model_id, category, sub_item, condition_expr, score, sub_item_cap, importance)
            VALUES (#{modelId}, #{category}, #{subItem}, #{condition}, #{score}, #{subItemCap}, #{importance})
            """)
    void insertScoreRatingItem(@Param("modelId") long modelId,
                               @Param("category") String category,
                               @Param("subItem") String subItem,
                               @Param("condition") String condition,
                               @Param("score") BigDecimal score,
                               @Param("subItemCap") BigDecimal subItemCap,
                               @Param("importance") String importance);

    @Select("SELECT min_score AS minScore, max_score AS maxScore, grade FROM rating_grade_band "
            + "WHERE rating_model_id = #{modelId} ORDER BY order_no")
    List<EngineIntegrationTestRows.GradeBandRow> findGradeBandsByModelId(@Param("modelId") long modelId);

    @Select("SELECT condition_expr AS conditionExpr, grade FROM rating_item "
            + "WHERE rating_model_id = #{modelId} ORDER BY id")
    List<EngineIntegrationTestRows.DirectItemRow> findDirectItemsByModelId(@Param("modelId") long modelId);

    @Select("SELECT category, sub_item AS subItem, condition_expr AS conditionExpr, score, "
            + "sub_item_cap AS subItemCap, importance FROM rating_item "
            + "WHERE rating_model_id = #{modelId} ORDER BY id")
    List<EngineIntegrationTestRows.ScoreItemRow> findScoreItemsByModelId(@Param("modelId") long modelId);

    @Select("SELECT COUNT(1) FROM rating_model WHERE id = #{modelId}")
    Integer countRatingModelById(@Param("modelId") long modelId);

    @Select("SELECT COUNT(1) FROM rating_grade_band WHERE rating_model_id = #{modelId}")
    Integer countGradeBandsByModelId(@Param("modelId") long modelId);

    @Select("SELECT COUNT(1) FROM rating_item WHERE rating_model_id = #{modelId}")
    Integer countRatingItemsByModelId(@Param("modelId") long modelId);

    // —— rule_package / rule_v2 ——

    @Delete("DELETE FROM rule_package_rule WHERE rule_package_id IN "
            + "(SELECT id FROM rule_package WHERE code LIKE #{pattern})")
    void deleteRulePackageRulesByPackageCodePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rule_package_score_band WHERE rule_package_id IN "
            + "(SELECT id FROM rule_package WHERE code LIKE #{pattern})")
    void deleteRulePackageScoreBandsByPackageCodePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rule_v2 WHERE code LIKE #{pattern}")
    void deleteRulesByCodePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rule_package WHERE code LIKE #{pattern}")
    void deleteRulePackagesByCodePattern(@Param("pattern") String pattern);

    @Insert("""
            INSERT INTO rule_package (code, name, trigger_mode, compute_mode, status, version)
            VALUES (#{code}, #{name}, #{triggerMode}, 'ONLINE', 'ENABLED', 1)
            """)
    void insertRulePackage(@Param("code") String code,
                           @Param("name") String name,
                           @Param("triggerMode") String triggerMode);

    @Select("SELECT id FROM rule_package WHERE code = #{code}")
    Long findRulePackageIdByCode(@Param("code") String code);

    @Insert("""
            INSERT INTO rule_v2 (code, name, rule_kind, risk_level_code, compiled_expr,
                priority, short_circuited, expr_version, version, status)
            VALUES (#{code}, #{code}, 'HIT', #{riskLevelCode}, #{compiledExpr}, 0, 0, 0, 1, #{status})
            """)
    void insertHitRule(@Param("code") String code,
                       @Param("riskLevelCode") String riskLevelCode,
                       @Param("compiledExpr") String compiledExpr,
                       @Param("status") String status);

    @Insert("""
            INSERT INTO rule_v2 (code, name, rule_kind, base_score, compiled_expr,
                priority, short_circuited, expr_version, version, status)
            VALUES (#{code}, #{code}, 'SCORE', #{baseScore}, #{compiledExpr}, 0, 0, 0, 1, #{status})
            """)
    void insertScoreRule(@Param("code") String code,
                         @Param("baseScore") BigDecimal baseScore,
                         @Param("compiledExpr") String compiledExpr,
                         @Param("status") String status);

    @Select("SELECT id FROM rule_v2 WHERE code = #{code}")
    Long findRuleIdByCode(@Param("code") String code);

    @Insert("INSERT INTO rule_package_rule (rule_package_id, rule_v2_id, priority) VALUES (#{packageId}, #{ruleId}, #{priority})")
    void bindRuleToPackage(@Param("packageId") long packageId,
                           @Param("ruleId") long ruleId,
                           @Param("priority") int priority);

    @Insert("""
            INSERT INTO rule_package_score_band (rule_package_id, lower, upper,
                lower_inclusive, upper_inclusive, risk_level_code, order_no)
            VALUES (#{packageId}, #{lower}, #{upper}, #{lowerInclusive}, #{upperInclusive}, #{riskLevelCode}, #{orderNo})
            """)
    void insertScoreBand(@Param("packageId") long packageId,
                         @Param("lower") BigDecimal lower,
                         @Param("upper") BigDecimal upper,
                         @Param("lowerInclusive") int lowerInclusive,
                         @Param("upperInclusive") int upperInclusive,
                         @Param("riskLevelCode") String riskLevelCode,
                         @Param("orderNo") int orderNo);

    @Select("SELECT status FROM rule_v2 WHERE id = #{ruleId}")
    String findRuleStatusById(@Param("ruleId") long ruleId);

    @Select("SELECT COUNT(1) FROM rule_package WHERE id = #{packageId}")
    Integer countRulePackageById(@Param("packageId") long packageId);

    @Select("SELECT COUNT(1) FROM rule_package_rule WHERE rule_package_id = #{packageId}")
    Integer countPackageRulesByPackageId(@Param("packageId") long packageId);

    @Select("SELECT COUNT(1) FROM rule_package_score_band WHERE rule_package_id = #{packageId}")
    Integer countScoreBandsByPackageId(@Param("packageId") long packageId);

    // —— decision_log（XT1 执行链路查询）——

    @Insert("""
            INSERT INTO decision_log (event_id, final_decision, hit_rules, elapsed_ms, group_status, created_at)
            VALUES (#{eventId}, #{finalDecision}, #{hitRules}, #{elapsedMs}, #{groupStatus}, NOW(3))
            """)
    void insertDecisionLog(@Param("eventId") String eventId,
                           @Param("finalDecision") String finalDecision,
                           @Param("hitRules") String hitRules,
                           @Param("elapsedMs") int elapsedMs,
                           @Param("groupStatus") String groupStatus);

    @Delete("DELETE FROM decision_log WHERE event_id LIKE #{pattern}")
    void deleteDecisionLogsByEventIdPattern(@Param("pattern") String pattern);
}
