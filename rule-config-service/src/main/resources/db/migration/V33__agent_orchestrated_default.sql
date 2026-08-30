-- 更新默认 Agent 策略：支持自主编排 + 突发大额特征
UPDATE agent_strategy
SET config_json = '{
  "llmMode": "ORCHESTRATED",
  "maxOrchestrationSteps": 6,
  "featureFields": ["merchantId", "amount", "eventTypeCode"],
  "llm": {
    "model": "gpt-4o-mini",
    "apiKeyEnv": "OPENAI_API_KEY",
    "systemPrompt": "你是支付风控 AI Agent。关注名单、突发巨额、与引擎结论一致性。",
    "temperature": 0.2
  },
  "tools": [
    {"id": "read_context", "enabled": true},
    {"id": "list_check", "enabled": true},
    {"id": "analyze_amount_spike", "enabled": true, "dailyAmtRef": "b2b_daily_amt", "countRef": "txn_cnt_1d", "spikeRatio": 3, "spikeAbsolute": 100000},
    {"id": "read_indicator", "enabled": true, "refs": [{"refName": "b2b_daily_amt", "windowDays": 1, "granularity": "DAY"}]},
    {"id": "compare_engine", "enabled": true}
  ],
  "rules": [
    {"when": "blackHit", "decision": "REJECT", "reason": "名单黑名单命中", "confidence": 0.95},
    {"when": "amount_spike", "decision": "REVIEW", "reason": "突发巨额交易，建议人工复核", "confidence": 0.9},
    {"when": "engine_reject", "decision": "REVIEW", "reason": "引擎已拒绝，建议复核", "confidence": 0.7}
  ],
  "defaultDecision": "PASS",
  "defaultConfidence": 0.75,
  "defaultReason": "未发现显著风险"
}'
WHERE code = 'default_agent';
