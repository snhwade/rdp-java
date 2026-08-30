package com.riskplatform.gateway.integration.support;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 网关集成测试造数/清理（仅 test scope）。 */
@Mapper
public interface GatewayIntegrationTestDataMapper {

    @Insert("""
            INSERT INTO engine_decision_record (event_id, correlation_id, merchant_id, event_type_code,
                event_time, engine_decision, final_decision, invoke_mode, detail_json, elapsed_ms, created_at)
            VALUES (#{eventId}, #{correlationId}, #{merchantId}, #{eventTypeCode}, NOW(),
                #{engineDecision}, #{finalDecision}, 'SYNC', #{detailJson}, #{elapsedMs}, NOW())
            """)
    void insertEngineDecisionRecord(@Param("eventId") String eventId,
                                    @Param("correlationId") String correlationId,
                                    @Param("merchantId") String merchantId,
                                    @Param("eventTypeCode") String eventTypeCode,
                                    @Param("engineDecision") String engineDecision,
                                    @Param("finalDecision") String finalDecision,
                                    @Param("detailJson") String detailJson,
                                    @Param("elapsedMs") long elapsedMs);

    @Delete("DELETE FROM engine_decision_record WHERE event_id LIKE #{pattern}")
    void deleteEngineDecisionRecordsByEventIdPattern(@Param("pattern") String pattern);

    @Delete("DELETE FROM engine_decision_record WHERE event_id = #{eventId}")
    void deleteEngineDecisionRecordByEventId(@Param("eventId") String eventId);

    @Delete("DELETE FROM risk_order WHERE event_id = #{eventId}")
    void deleteRiskOrderByEventId(@Param("eventId") String eventId);

    @Delete("DELETE FROM list_record WHERE reason LIKE #{pattern}")
    void deleteListRecordsByReasonPattern(@Param("pattern") String pattern);

    @org.apache.ibatis.annotations.Select("SELECT COUNT(1) FROM engine_decision_record WHERE event_id = #{eventId}")
    Integer countEngineDecisionRecordByEventId(@Param("eventId") String eventId);

    @org.apache.ibatis.annotations.Select("SELECT COUNT(1) FROM risk_order WHERE event_id = #{eventId}")
    Integer countRiskOrderByEventId(@Param("eventId") String eventId);
}
