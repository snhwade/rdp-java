package com.riskplatform.rating.integration.support;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 商户评级集成测试断言/清理（仅 test scope）。 */
@Mapper
public interface RatingIntegrationTestDataMapper {

    @Select("SELECT score FROM merchant_rating WHERE merchant_id = #{merchantId}")
    Integer findScoreByMerchantId(@Param("merchantId") String merchantId);

    @Select("SELECT level FROM merchant_rating WHERE merchant_id = #{merchantId}")
    String findLevelByMerchantId(@Param("merchantId") String merchantId);

    @Delete("DELETE FROM merchant_rating WHERE merchant_id LIKE #{pattern}")
    void deleteMerchantRatingsByIdPattern(@Param("pattern") String pattern);
}
