-- Agent 策略：纯 LLM 自主编排（移除 rules 固定规则回退，由大模型根据 toolTrace 推理）
UPDATE agent_strategy
SET config_json = '{
  "llmMode": "ORCHESTRATED",
  "maxOrchestrationSteps": 8,
  "featureFields": ["merchantId", "amount", "eventTypeCode", "country"],
  "knownRisks": [
    {"id": "blacklist", "name": "黑名单命中", "description": "主体在精确黑名单", "signalKeys": ["blackHit"], "suggestTools": ["list_check"]},
    {"id": "watchlist", "name": "关注名单", "description": "主体在关注名单", "signalKeys": ["watchHit"], "suggestTools": ["list_check"]},
    {"id": "amount_spike", "name": "突发巨额", "description": "单笔金额相对历史异常", "signalKeys": ["amountSpike"], "suggestTools": ["analyze_amount_spike"]},
    {"id": "engine_reject", "name": "引擎拒绝", "description": "规则引擎已给出拒绝", "signalKeys": ["engineDecision"], "suggestTools": ["compare_engine"]}
  ],
  "llm": {
    "provider": "deepseek",
    "model": "deepseek-v4-pro",
    "apiKeyEnv": "DEEPSEEK_API_KEY",
    "systemPrompt": "You are a senior payment risk expert. Collect evidence via tools and infer PASS/REVIEW/REJECT from toolTrace. Do not use fixed rule tables or fixed tool order.",
    "temperature": 0.2
  },
  "tools": [
    {"id": "read_context", "enabled": true},
    {"id": "list_check", "enabled": true},
    {"id": "analyze_amount_spike", "enabled": true, "spikeRatio": 3, "spikeAbsolute": 100000},
    {"id": "read_indicator", "enabled": true, "refs": [{"refName": "b2b_daily_amt", "windowDays": 1, "granularity": "DAY"}]},
    {"id": "compare_engine", "enabled": true},
    {"id": "check_known_risks", "enabled": true}
  ],
  "defaultDecision": "PASS",
  "defaultConfidence": 0.75,
  "defaultReason": "证据不足时保守放行"
}'
WHERE code = 'default_agent';
