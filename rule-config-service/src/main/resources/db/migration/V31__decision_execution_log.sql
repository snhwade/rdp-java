-- 引擎决策与 AI Agent 决策执行记录（事中双轨，以 event_id / correlation_id 关联）

CREATE TABLE engine_decision_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL COMMENT '全局事件标识 evt-{uuid32}',
    correlation_id  VARCHAR(36)  NOT NULL COMMENT '关联 UUID（32 位十六进制，两轨统一关联键）',
    merchant_id     VARCHAR(64)  NULL,
    event_type_code VARCHAR(64)  NOT NULL,
    event_time      DATETIME     NULL,
    engine_decision VARCHAR(32)  NOT NULL COMMENT '规则引擎原始决策',
    final_decision  VARCHAR(32)  NOT NULL COMMENT '网关合并名单/筛查后的对外决策',
    invoke_mode     VARCHAR(32)  NULL,
    rule_package_id BIGINT       NULL,
    decision_flow_id BIGINT      NULL,
    detail_json     JSON         NULL COMMENT '命中规则、决策流 trace 等',
    elapsed_ms      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_engine_decision_event (event_id),
    KEY idx_engine_decision_corr (correlation_id),
    KEY idx_engine_decision_merchant_time (merchant_id, event_time)
) COMMENT '规则引擎决策执行记录';

CREATE TABLE ai_decision_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL,
    correlation_id  VARCHAR(36)  NOT NULL,
    merchant_id     VARCHAR(64)  NULL,
    event_type_code VARCHAR(64)  NULL,
    event_time      DATETIME     NULL,
    status          VARCHAR(16)  NOT NULL COMMENT 'PENDING/SUCCESS/FAILED',
    agent_decision  VARCHAR(32)  NULL,
    confidence      DOUBLE       NULL,
    reason          VARCHAR(1024) NULL,
    engine_decision VARCHAR(32)  NULL COMMENT '对比用引擎决策快照',
    divergence      TINYINT(1)   NULL COMMENT '与引擎决策是否不一致',
    trace_json      JSON         NULL COMMENT 'Agent 工具调用与推理链',
    fail_reason     VARCHAR(512) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME     NULL,
    UNIQUE KEY uk_ai_decision_event (event_id),
    KEY idx_ai_decision_corr (correlation_id),
    KEY idx_ai_decision_merchant_time (merchant_id, event_time),
    KEY idx_ai_decision_status (status)
) COMMENT 'AI Agent 决策执行记录';
