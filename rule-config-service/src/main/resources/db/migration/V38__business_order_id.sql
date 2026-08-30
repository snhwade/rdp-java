-- 业务订单号：同一订单可多次风控调用（调用维度=eventId，订单维度=business_order_id）
ALTER TABLE risk_order
    ADD COLUMN business_order_id VARCHAR(128) NULL COMMENT '业务订单号（context.orderId 等）' AFTER event_id,
    ADD KEY idx_risk_order_business_order (business_order_id);

ALTER TABLE engine_decision_record
    ADD COLUMN business_order_id VARCHAR(128) NULL COMMENT '业务订单号' AFTER correlation_id,
    ADD KEY idx_engine_decision_business_order (business_order_id);

ALTER TABLE ai_decision_record
    ADD COLUMN business_order_id VARCHAR(128) NULL COMMENT '业务订单号' AFTER correlation_id,
    ADD KEY idx_ai_decision_business_order (business_order_id);
