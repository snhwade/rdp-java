package com.riskplatform.bff.integration.support;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** BFF 集成测试清理（仅 test scope）。 */
@Mapper
public interface BffIntegrationTestDataMapper {

    @Delete("DELETE FROM merchant_rating WHERE merchant_id LIKE #{pattern}")
    void deleteMerchantRatingsByIdPattern(@Param("pattern") String pattern);

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

    @org.apache.ibatis.annotations.Select("SELECT COUNT(1) FROM engine_decision_record WHERE event_id = #{eventId}")
    Integer countEngineDecisionRecordByEventId(@Param("eventId") String eventId);
}
