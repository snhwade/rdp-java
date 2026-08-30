package com.riskplatform.ruleconfig.integration.support;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 集成测试造数/断言/清理（仅 test scope，替代 JdbcTemplate 直写 SQL）。 */
@Mapper
public interface IntegrationTestDataMapper {

    // —— indicator / user（优化模块） ——

    @Select("SELECT description FROM indicator_definition WHERE id = #{id}")
    String findIndicatorDescription(@Param("id") long id);

    @Select("SELECT COUNT(1) FROM rule_v2 WHERE compiled_expr LIKE #{pattern}")
    Integer countRulesByCompiledExprPattern(@Param("pattern") String pattern);

    @Insert("""
            INSERT INTO rule_v2 (code, name, rule_kind, event_type_code, risk_level_code, base_score,
                priority, status, condition_json, compiled_expr)
            VALUES (#{code}, #{name}, 'HIT', #{eventCode}, 'HIGH', 80, 0, 'OFFLINE', #{conditionJson}, #{compiledExpr})
            """)
    void insertReferencingRule(@Param("code") String code,
                               @Param("name") String name,
                               @Param("eventCode") String eventCode,
                               @Param("conditionJson") String conditionJson,
                               @Param("compiledExpr") String compiledExpr);

    @Select("SELECT id FROM rule_v2 WHERE code = #{code}")
    Long findRuleIdByCode(@Param("code") String code);

    @Delete("DELETE FROM rule_v2 WHERE id = #{id}")
    void deleteRuleById(@Param("id") long id);

    @Insert("""
            INSERT INTO indicator_runtime_stats (ref_name, last_accumulate_at, read_miss_count)
            VALUES (#{refName}, NOW(3), #{readMissCount})
            ON DUPLICATE KEY UPDATE last_accumulate_at = VALUES(last_accumulate_at),
                read_miss_count = VALUES(read_miss_count)
            """)
    void upsertRuntimeStats(@Param("refName") String refName, @Param("readMissCount") long readMissCount);

    @Select("SELECT name FROM indicator_definition WHERE id = #{id}")
    String findIndicatorName(@Param("id") long id);

    @Select("SELECT enabled FROM sys_user WHERE id = #{id}")
    Integer findUserEnabled(@Param("id") long id);

    @Select("SELECT password_hash FROM sys_user WHERE username = #{username}")
    String findPasswordHash(@Param("username") String username);

    @Delete("DELETE FROM sys_user WHERE username LIKE #{pattern}")
    void deleteUsersByUsernamePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rule_v2 WHERE code LIKE #{pattern}")
    void deleteRulesByCodePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM indicator_definition_snapshot WHERE indicator_definition_id IN "
            + "(SELECT id FROM indicator_definition WHERE ref_name LIKE #{pattern1} OR ref_name LIKE #{pattern2})")
    void deleteIndicatorSnapshots(@Param("pattern1") String pattern1, @Param("pattern2") String pattern2);

    @Delete("DELETE FROM indicator_runtime_stats WHERE ref_name LIKE #{pattern1} OR ref_name LIKE #{pattern2}")
    void deleteRuntimeStats(@Param("pattern1") String pattern1, @Param("pattern2") String pattern2);

    @Delete("DELETE FROM indicator_definition WHERE ref_name LIKE #{pattern1} OR ref_name LIKE #{pattern2}")
    void deleteIndicators(@Param("pattern1") String pattern1, @Param("pattern2") String pattern2);

    @Delete("DELETE FROM event_type WHERE code LIKE #{pattern}")
    void deleteEventTypes(@Param("pattern") String pattern);

    @Delete("DELETE FROM scenario WHERE code LIKE #{pattern}")
    void deleteScenarios(@Param("pattern") String pattern);

    // —— event_field / field_library ——

    @Delete("DELETE FROM event_field WHERE event_type_code LIKE #{pattern}")
    void deleteEventFieldsByEventCodePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM field_library WHERE code LIKE #{pattern}")
    void deleteFieldLibraryByCodePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM derived_field WHERE event_type_code LIKE #{pattern1} OR name LIKE #{pattern2}")
    void deleteDerivedFieldsByPattern(@Param("pattern1") String pattern1, @Param("pattern2") String pattern2);

    @Delete("DELETE FROM field_library WHERE code LIKE #{pattern1} OR name LIKE #{pattern2}")
    void deleteFieldLibraryByCodeOrNamePattern(@Param("pattern1") String pattern1, @Param("pattern2") String pattern2);

    @Select("SELECT derived FROM event_field WHERE id = #{id}")
    Integer findEventFieldDerivedFlag(@Param("id") long id);

    @Select("SELECT COUNT(1) FROM event_field WHERE event_type_code = #{eventCode} AND field_id = #{fieldId}")
    Integer countEventFieldByEventAndField(@Param("eventCode") String eventCode, @Param("fieldId") long fieldId);

    @Select("SELECT COUNT(1) FROM event_field WHERE event_type_code = #{eventCode}")
    Integer countEventFieldsByEventCode(@Param("eventCode") String eventCode);

    @Select("SELECT COUNT(1) FROM field_library WHERE code = #{code}")
    Integer countFieldLibraryByCode(@Param("code") String code);

    @Select("SELECT COUNT(1) FROM event_type WHERE code = #{code}")
    Integer countEventTypeByCode(@Param("code") String code);

    @Insert("""
            INSERT INTO rule_v2 (code, name, rule_kind, event_type_code, condition_json)
            VALUES (#{code}, #{name}, 'HIT', #{eventCode}, #{conditionJson})
            """)
    void insertEventFieldReferencingRule(@Param("code") String code,
                                         @Param("name") String name,
                                         @Param("eventCode") String eventCode,
                                         @Param("conditionJson") String conditionJson);

    @Delete("DELETE FROM rule_v2 WHERE code = #{code}")
    void deleteRuleByCode(@Param("code") String code);

    // —— strategy_def ——

    @Delete("DELETE FROM strategy_def WHERE code LIKE #{pattern}")
    void deleteStrategyDefByCodePattern(@Param("pattern") String pattern);

    @Select("SELECT category, priority, scope_scenario_id AS scopeScenarioId, any_scope AS anyScope, name "
            + "FROM strategy_def WHERE id = #{id}")
    IntegrationTestRows.StrategyDefRow findStrategyDefById(@Param("id") long id);

    @Select("SELECT category FROM strategy_def WHERE code = #{code}")
    String findStrategyCategoryByCode(@Param("code") String code);

    @Select("SELECT COUNT(1) FROM strategy_def WHERE code = #{code}")
    Integer countStrategyDefByCode(@Param("code") String code);

    @Insert("INSERT INTO strategy_def (category, code, name, status) VALUES ('NOTIFY', #{code}, #{name}, 'ENABLED')")
    void insertNotifyStrategy(@Param("code") String code, @Param("name") String name);

    // —— rule_package ——

    @Delete("DELETE FROM rule_package_rule WHERE rule_package_id IN "
            + "(SELECT id FROM rule_package WHERE code LIKE #{pattern})")
    void deleteRulePackageRulesByPackageCodePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rule_package_event WHERE rule_package_id IN "
            + "(SELECT id FROM rule_package WHERE code LIKE #{pattern})")
    void deleteRulePackageEventsByPackageCodePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rule_package_scenario WHERE rule_package_id IN "
            + "(SELECT id FROM rule_package WHERE code LIKE #{pattern})")
    void deleteRulePackageScenariosByPackageCodePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rule_package_score_band WHERE rule_package_id IN "
            + "(SELECT id FROM rule_package WHERE code LIKE #{pattern})")
    void deleteRulePackageScoreBandsByPackageCodePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rule_package WHERE code LIKE #{pattern}")
    void deleteRulePackagesByCodePattern(@Param("pattern") String pattern);

    @Select("SELECT code, name, trigger_mode AS triggerMode FROM rule_package WHERE id = #{id}")
    IntegrationTestRows.RulePackageRow findRulePackageById(@Param("id") long id);

    @Insert("""
            INSERT INTO rule_v2 (code, name, rule_package_id, rule_kind, event_type_code,
                risk_level_code, base_score, priority, status)
            VALUES (#{code}, #{name}, #{rulePackageId}, 'HIT', #{eventCode}, 'HIGH', 80, 0, #{status})
            """)
    void insertSeedRule(@Param("code") String code,
                        @Param("name") String name,
                        @Param("rulePackageId") long rulePackageId,
                        @Param("eventCode") String eventCode,
                        @Param("status") String status);

    @Select("SELECT COUNT(1) FROM rule_v2 WHERE rule_package_id = #{rulePackageId} AND status = #{status}")
    Long countRulesByPackageIdAndStatus(@Param("rulePackageId") long rulePackageId, @Param("status") String status);

    @Select("SELECT status FROM rule_v2 WHERE id = #{id}")
    String findRuleStatusById(@Param("id") long id);

    @Select("SELECT COUNT(1) FROM rule_v2 WHERE id = #{id}")
    Integer countRuleById(@Param("id") long id);

    // —— decision_flow ——

    @Delete("DELETE FROM decision_flow_version WHERE decision_flow_id IN "
            + "(SELECT id FROM decision_flow WHERE name LIKE #{pattern})")
    void deleteDecisionFlowVersionsByFlowNamePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM decision_flow WHERE name LIKE #{pattern}")
    void deleteDecisionFlowsByNamePattern(@Param("pattern") String pattern);

    @Select("SELECT name, event_type_code AS eventTypeCode, start_node_id AS startNodeId, "
            + "nodes_json AS nodesJson, edges_json AS edgesJson FROM decision_flow WHERE id = #{id}")
    IntegrationTestRows.DecisionFlowDetailRow findDecisionFlowDetailById(@Param("id") long id);

    @Select("SELECT name, event_type_code AS eventTypeCode, start_node_id AS startNodeId "
            + "FROM decision_flow WHERE id = #{id}")
    IntegrationTestRows.DecisionFlowRow findDecisionFlowById(@Param("id") long id);

    @Select("SELECT version, status, snapshot_json AS snapshotJson FROM decision_flow_version "
            + "WHERE decision_flow_id = #{flowId} LIMIT 1")
    IntegrationTestRows.DecisionFlowVersionRow findDecisionFlowVersionByFlowId(@Param("flowId") long flowId);

    @Select("SELECT version, snapshot_json AS snapshotJson FROM decision_flow_version "
            + "WHERE decision_flow_id = #{flowId} ORDER BY version ASC")
    List<IntegrationTestRows.VersionSnapshotRow> findDecisionFlowVersionSnapshots(@Param("flowId") long flowId);

    @Select("SELECT snapshot_json FROM decision_flow_version "
            + "WHERE decision_flow_id = #{flowId} ORDER BY version DESC LIMIT 1")
    String findLatestDecisionFlowSnapshotJson(@Param("flowId") long flowId);

    @Select("SELECT COUNT(1) FROM decision_flow WHERE name = #{name}")
    Integer countDecisionFlowByName(@Param("name") String name);

    @Select("SELECT COUNT(1) FROM decision_flow_version WHERE decision_flow_id = #{flowId}")
    Integer countDecisionFlowVersionsByFlowId(@Param("flowId") long flowId);

    @Select("SELECT COUNT(1) FROM decision_flow_version WHERE decision_flow_id = #{flowId} AND status = 'ONLINE'")
    Integer countOnlineDecisionFlowVersionsByFlowId(@Param("flowId") long flowId);

    @Select("SELECT status FROM decision_flow_version WHERE decision_flow_id = #{flowId} AND version = #{version}")
    String findDecisionFlowVersionStatus(@Param("flowId") long flowId, @Param("version") int version);

    // —— rating_model ——

    @Delete("DELETE FROM rating_grade_band WHERE rating_model_id IN "
            + "(SELECT id FROM rating_model WHERE name LIKE #{pattern})")
    void deleteRatingGradeBandsByModelNamePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rating_item WHERE rating_model_id IN "
            + "(SELECT id FROM rating_model WHERE name LIKE #{pattern})")
    void deleteRatingItemsByModelNamePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rating_model_version WHERE rating_model_id IN "
            + "(SELECT id FROM rating_model WHERE name LIKE #{pattern})")
    void deleteRatingModelVersionsByModelNamePattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM rating_model WHERE name LIKE #{pattern}")
    void deleteRatingModelsByNamePattern(@Param("pattern") String pattern);

    @Select("SELECT name, event_type_code AS eventTypeCode, execution_mode AS executionMode, "
            + "subject, grading_mode AS gradingMode, status, version FROM rating_model WHERE id = #{id}")
    IntegrationTestRows.RatingModelDetailRow findRatingModelDetailById(@Param("id") long id);

    @Select("SELECT version, snapshot_json AS snapshotJson FROM rating_model_version "
            + "WHERE rating_model_id = #{modelId} LIMIT 1")
    IntegrationTestRows.VersionSnapshotRow findRatingModelVersionSnapshot(@Param("modelId") long modelId);

    @Select("SELECT version, snapshot_json AS snapshotJson FROM rating_model_version "
            + "WHERE rating_model_id = #{modelId} ORDER BY version ASC")
    List<IntegrationTestRows.VersionSnapshotRow> findRatingModelVersionSnapshots(@Param("modelId") long modelId);

    @Select("SELECT min_score AS minScore, max_score AS maxScore, grade, order_no AS orderNo "
            + "FROM rating_grade_band WHERE rating_model_id = #{modelId} ORDER BY order_no ASC")
    List<IntegrationTestRows.RatingGradeBandRow> findRatingGradeBandsByModelId(@Param("modelId") long modelId);

    @Select("SELECT version FROM rating_model WHERE id = #{id}")
    Integer findRatingModelVersionNumber(@Param("id") long id);

    @Select("SELECT COUNT(1) FROM rating_model_version WHERE rating_model_id = #{modelId}")
    Integer countRatingModelVersionsByModelId(@Param("modelId") long modelId);

    @Select("SELECT COUNT(1) FROM rating_grade_band WHERE rating_model_id = #{modelId}")
    Integer countRatingGradeBandsByModelId(@Param("modelId") long modelId);

    @Select("SELECT COUNT(1) FROM rating_model WHERE name = #{name}")
    Integer countRatingModelByName(@Param("name") String name);

    @Select("SELECT status FROM rating_model WHERE id = #{id}")
    String findRatingModelStatus(@Param("id") long id);

    // —— seed idempotency ——

    @Select("SELECT COUNT(1) FROM event_field WHERE derived = 1")
    Integer countDerivedEventFields();

    @Select("SELECT COUNT(1) FROM decision_flow_version WHERE status = 'ONLINE'")
    Integer countOnlineDecisionFlowVersions();

    @Select("SELECT COUNT(1) FROM scenario")
    Integer countScenarioRows();

    @Select("SELECT COUNT(1) FROM scenario_event")
    Integer countScenarioEventRows();

    @Select("SELECT COUNT(1) FROM event_type")
    Integer countEventTypeRows();

    @Select("SELECT COUNT(1) FROM field_library")
    Integer countFieldLibraryRows();

    @Select("SELECT COUNT(1) FROM event_field")
    Integer countEventFieldRows();

    @Select("SELECT COUNT(1) FROM strategy_def")
    Integer countStrategyDefRows();

    @Select("SELECT COUNT(1) FROM rule_package")
    Integer countRulePackageRows();

    @Select("SELECT COUNT(1) FROM rule_package_event")
    Integer countRulePackageEventRows();

    @Select("SELECT COUNT(1) FROM rule_v2")
    Integer countRuleV2Rows();

    @Select("SELECT COUNT(1) FROM rule_package_rule")
    Integer countRulePackageRuleRows();

    @Select("SELECT COUNT(1) FROM decision_flow")
    Integer countDecisionFlowRows();

    @Select("SELECT COUNT(1) FROM decision_flow_version")
    Integer countDecisionFlowVersionRows();

    @Select("SELECT COUNT(1) FROM rating_model")
    Integer countRatingModelRows();

    @Select("SELECT COUNT(1) FROM rating_grade_band")
    Integer countRatingGradeBandRows();

    @Select("SELECT COUNT(1) FROM rating_item")
    Integer countRatingItemRows();

    @Select("SELECT COUNT(1) FROM rating_model_version")
    Integer countRatingModelVersionRows();

    @Select("SELECT column_name AS columnName, column_type AS columnType, is_nullable AS isNullable, "
            + "column_key AS columnKey FROM information_schema.columns "
            + "WHERE table_schema = DATABASE() AND table_name = #{tableName} ORDER BY ordinal_position")
    List<IntegrationTestRows.ColumnMetaRow> findColumnMetaByTableName(@Param("tableName") String tableName);

    @Select("<script>SELECT COUNT(1) FROM scenario WHERE code IN "
            + "<foreach item='c' collection='codes' open='(' separator=',' close=')'>#{c}</foreach></script>")
    Integer countScenariosByCodes(@Param("codes") List<String> codes);

    @Select("<script>SELECT COUNT(1) FROM event_type WHERE code IN "
            + "<foreach item='c' collection='codes' open='(' separator=',' close=')'>#{c}</foreach></script>")
    Integer countEventTypesByCodes(@Param("codes") List<String> codes);

    @Select("<script>SELECT COUNT(1) FROM field_library WHERE code IN "
            + "<foreach item='c' collection='codes' open='(' separator=',' close=')'>#{c}</foreach></script>")
    Integer countFieldLibraryByCodes(@Param("codes") List<String> codes);

    @Select("<script>SELECT COUNT(1) FROM strategy_def WHERE code IN "
            + "<foreach item='c' collection='codes' open='(' separator=',' close=')'>#{c}</foreach></script>")
    Integer countStrategyDefByCodes(@Param("codes") List<String> codes);

    @Select("<script>SELECT COUNT(1) FROM rule_package WHERE code IN "
            + "<foreach item='c' collection='codes' open='(' separator=',' close=')'>#{c}</foreach></script>")
    Integer countRulePackagesByCodes(@Param("codes") List<String> codes);

    @Select("<script>SELECT COUNT(1) FROM rule_v2 WHERE code IN "
            + "<foreach item='c' collection='codes' open='(' separator=',' close=')'>#{c}</foreach></script>")
    Integer countRulesByCodes(@Param("codes") List<String> codes);

    @Select("<script>SELECT COUNT(1) FROM rating_model WHERE name IN "
            + "<foreach item='c' collection='names' open='(' separator=',' close=')'>#{c}</foreach></script>")
    Integer countRatingModelsByNames(@Param("names") List<String> names);

    @Select("<script>SELECT COUNT(1) FROM rule_v2 WHERE code IN "
            + "<foreach item='c' collection='codes' open='(' separator=',' close=')'>#{c}</foreach> "
            + "AND status = #{status}</script>")
    Integer countSeedRulesByCodesAndStatus(@Param("codes") List<String> codes, @Param("status") String status);

    @Select("<script>SELECT COUNT(1) FROM rating_model WHERE name IN "
            + "<foreach item='c' collection='names' open='(' separator=',' close=')'>#{c}</foreach> "
            + "AND grading_mode = #{gradingMode}</script>")
    Integer countSeedRatingModelsByNamesAndMode(@Param("names") List<String> names,
                                                @Param("gradingMode") String gradingMode);

    @Select("<script>SELECT ${keyColumn} AS k, COUNT(1) AS c FROM ${table} WHERE ${keyColumn} IN "
            + "<foreach item='c' collection='codes' open='(' separator=',' close=')'>#{c}</foreach> "
            + "GROUP BY ${keyColumn} HAVING c &gt; 1</script>")
    List<IntegrationTestRows.DuplicateKeyRow> findDuplicateKeys(@Param("table") String table,
                                                                  @Param("keyColumn") String keyColumn,
                                                                  @Param("codes") List<String> codes);

    @Select("SELECT COUNT(1) FROM (SELECT 1 FROM event_field "
            + "GROUP BY event_type_code, field_id HAVING COUNT(1) > 1) dup")
    Integer countDuplicateEventFieldGroups();

    @Select("SELECT COUNT(1) FROM (SELECT 1 FROM decision_flow WHERE name = '支付决策流' "
            + "GROUP BY name, event_type_code HAVING COUNT(1) > 1) dup")
    Integer countDuplicateSeedDecisionFlowGroups();

    @Select("SELECT COUNT(1) FROM (SELECT 1 FROM decision_flow_version "
            + "GROUP BY decision_flow_id, version HAVING COUNT(1) > 1) dup")
    Integer countDuplicateDecisionFlowVersionGroups();

    @Select("SELECT COUNT(1) FROM (SELECT 1 FROM rating_model_version "
            + "GROUP BY rating_model_id, version HAVING COUNT(1) > 1) dup")
    Integer countDuplicateRatingModelVersionGroups();
}
