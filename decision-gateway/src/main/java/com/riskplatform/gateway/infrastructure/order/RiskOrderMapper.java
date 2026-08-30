package com.riskplatform.gateway.infrastructure.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * risk_order 表 Mapper（R10 + 订单维度聚合）。
 */
@Mapper
public interface RiskOrderMapper extends BaseMapper<RiskOrderPO> {

    @Select("""
            <script>
            SELECT
              IFNULL(business_order_id, event_id) AS business_order_id,
              MAX(merchant_id) AS merchant_id,
              MAX(event_type_code) AS event_type_code,
              COUNT(1) AS invocation_count,
              MAX(event_time) AS last_event_time,
              SUBSTRING_INDEX(GROUP_CONCAT(final_decision ORDER BY event_time DESC SEPARATOR '||'), '||', 1) AS latest_final_decision
            FROM risk_order
            <where>
              <if test="businessOrderId != null and businessOrderId != ''">
                AND IFNULL(business_order_id, event_id) = #{businessOrderId}
              </if>
              <if test="merchantId != null and merchantId != ''">
                AND merchant_id = #{merchantId}
              </if>
              <if test="eventTypeCode != null and eventTypeCode != ''">
                AND event_type_code = #{eventTypeCode}
              </if>
              <if test="startTime != null">
                AND event_time &gt;= #{startTime}
              </if>
              <if test="endTime != null">
                AND event_time &lt;= #{endTime}
              </if>
            </where>
            GROUP BY IFNULL(business_order_id, event_id)
            ORDER BY last_event_time DESC
            </script>
            """)
    IPage<BusinessOrderSummaryRow> pageBusinessOrders(
            Page<BusinessOrderSummaryRow> page,
            @Param("businessOrderId") String businessOrderId,
            @Param("merchantId") String merchantId,
            @Param("eventTypeCode") String eventTypeCode,
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime);
}
