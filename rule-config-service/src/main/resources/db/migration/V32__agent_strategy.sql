-- AI Agent 策略配置（可配置工具链 + 规则 + 可选 LLM）
CREATE TABLE IF NOT EXISTS agent_strategy (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code               VARCHAR(64)  NOT NULL COMMENT '策略编码',
    name               VARCHAR(128) NOT NULL COMMENT '策略名称',
    event_type_codes   LONGTEXT     NOT NULL COMMENT '适用事件类型 JSON 数组，* 表示兜底',
    config_json        LONGTEXT     NOT NULL COMMENT 'Agent 运行时配置 JSON',
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    create_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_strategy_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent 策略配置';

INSERT INTO agent_strategy (code, name, event_type_codes, config_json, status)
SELECT 'default_agent', '默认 Agent 策略', '["*"]',
'{
  "llmMode": "HEURISTIC",
  "tools": [
    {"id": "read_context", "enabled": true},
    {"id": "list_check", "enabled": true},
    {"id": "read_feature", "enabled": true, "fields": ["amount"]},
    {"id": "compare_engine", "enabled": true}
  ],
  "rules": [
    {"when": "blackHit", "decision": "REJECT", "reason": "名单黑名单命中", "confidence": 0.95},
    {"when": "amount_gt", "threshold": 100000, "decision": "REVIEW", "reason": "交易金额超过 10 万，建议人工复核", "confidence": 0.88},
    {"when": "watchHit", "decision": "REVIEW", "reason": "关注名单命中", "confidence": 0.82},
    {"when": "engine_reject", "decision": "REVIEW", "reason": "引擎已拒绝，Agent 建议复核引擎结论", "confidence": 0.7}
  ],
  "defaultDecision": "PASS",
  "defaultConfidence": 0.75,
  "defaultReason": "上下文无显著风险信号"
}',
'ENABLED'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM agent_strategy WHERE code = 'default_agent');
